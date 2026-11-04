package br.com.cavaleiros.mineradores.mining

import br.com.cavaleiros.mineradores.wallet.Hash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class ConnectionState(val label: String) {
    DISCONNECTED("desconectado"),
    CONNECTING("conectando"),
    CONNECTED("conectado"),
    AUTHORIZED("autorizado"),
    ERROR("erro")
}

class MiningConfig(
    val host: String,
    val port: Int,
    val tls: Boolean,
    val username: String,
    val password: String,
    /** Threads ativas no inicio (0..maxThreads); ajustavel depois com [MiningEngine.setLoad]. */
    val threads: Int,
    /** 0..100: fracao do tempo em que as threads ativas trabalham (controle termico). */
    val intensity: Int,
    /** Workers criados (normalmente o numero de nucleos); so [threads] deles trabalham. */
    val maxThreads: Int = threads,
    val suggestedDifficulty: Double?,
    val label: String
)

data class MiningStats(
    val running: Boolean = false,
    val paused: Boolean = false,
    val pauseReason: String? = null,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val poolLabel: String = "",
    val poolAddress: String = "",
    val hashrate: Double = 0.0,
    val hashrateAverage: Double = 0.0,
    val totalHashes: Long = 0,
    val sharesAccepted: Int = 0,
    val sharesRejected: Int = 0,
    val sharesSubmitted: Int = 0,
    val shareDifficulty: Double = 0.0,
    val networkDifficulty: Double = 0.0,
    val bestShareDifficulty: Double = 0.0,
    val blocksFound: Int = 0,
    val currentJobId: String? = null,
    val blockHeight: Int? = null,
    val prevHash: String? = null,
    val startedAt: Long = 0,
    val threads: Int = 0,
    val lastError: String? = null,
    val log: List<String> = emptyList()
)

/**
 * Motor de mineracao: mantem a conexao Stratum, distribui jobs entre threads
 * de trabalho, valida shares contra o alvo e os envia ao pool.
 *
 * Toda a comunicacao com a UI acontece pelo [stats] (StateFlow).
 */
class MiningEngine {
    private val _stats = MutableStateFlow(MiningStats())
    val stats: StateFlow<MiningStats> = _stats

    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val hashCounter = AtomicLong(0)
    private val jobGeneration = AtomicInteger(0)

    @Volatile private var config: MiningConfig? = null
    @Volatile private var client: StratumClient? = null
    @Volatile private var currentJob: StratumJob? = null
    @Volatile private var shareTarget: BigInteger = Target.DIFF1
    @Volatile private var workers: List<Worker> = emptyList()
    @Volatile private var managerThread: Thread? = null
    @Volatile private var statsThread: Thread? = null
    /** Quantos workers efetivamente trabalham (0..workers.size); ajustavel em tempo real. */
    @Volatile private var activeThreads: Int = 0
    /** 0..100: fracao do tempo em que cada worker ativo trabalha; ajustavel em tempo real. */
    @Volatile private var intensityNow: Int = 100

    private val submitted = java.util.concurrent.ConcurrentHashMap<Int, Double>()
    private val logLines = ArrayDeque<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val isRunning: Boolean get() = running.get()

    @Synchronized
    fun start(cfg: MiningConfig) {
        if (running.get()) return
        running.set(true)
        paused.set(false)
        config = cfg
        activeThreads = cfg.threads.coerceIn(0, cfg.maxThreads.coerceAtLeast(1))
        intensityNow = cfg.intensity.coerceIn(0, 100)
        hashCounter.set(0)
        submitted.clear()
        synchronized(logLines) { logLines.clear() }
        _stats.value = MiningStats(
            running = true,
            connection = ConnectionState.CONNECTING,
            poolLabel = cfg.label,
            poolAddress = "${cfg.host}:${cfg.port}${if (cfg.tls) " (TLS)" else ""}",
            startedAt = System.currentTimeMillis(),
            threads = activeThreads
        )
        log("iniciando $activeThreads de ${cfg.maxThreads.coerceAtLeast(1)} thread(s) a $intensityNow% -> ${cfg.host}:${cfg.port}")
        startWorkers(cfg)
        managerThread = Thread({ connectionLoop(cfg) }, "stratum-manager").apply { isDaemon = true; start() }
        statsThread = Thread({ statsLoop() }, "mining-stats").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        if (!running.getAndSet(false)) return
        paused.set(false)
        currentJob = null
        jobGeneration.incrementAndGet()
        client?.close("mineracao parada")
        client = null
        workers.forEach { it.interrupt() }
        workers = emptyList()
        managerThread?.interrupt()
        statsThread?.interrupt()
        log("mineracao parada")
        _stats.update { it.copy(running = false, paused = false, pauseReason = null, connection = ConnectionState.DISCONNECTED, hashrate = 0.0) }
    }

    /** Ajusta threads ativas e intensidade em tempo real, sem reconectar (barra de potencia). */
    fun setLoad(threads: Int, intensity: Int) {
        if (!running.get()) return
        val t = threads.coerceIn(0, workers.size)
        val i = intensity.coerceIn(0, 100)
        if (t == activeThreads && i == intensityNow) return
        activeThreads = t
        intensityNow = i
        _stats.update { it.copy(threads = t) }
        log(if (t == 0 || i == 0) "potencia 0%: em espera (conectado, sem calcular hashes)" else "potencia ajustada: $t thread(s) a $i%")
    }

    /** Pausa as threads (ex.: bateria baixa / aquecimento) mantendo a conexao. */
    fun pause(reason: String) {
        if (!running.get() || paused.getAndSet(true)) return
        log("pausado: $reason")
        _stats.update { it.copy(paused = true, pauseReason = reason, hashrate = 0.0) }
    }

    fun resume() {
        if (!running.get() || !paused.getAndSet(false)) return
        log("retomando mineracao")
        _stats.update { it.copy(paused = false, pauseReason = null) }
    }

    // ------------------------------------------------------------------ conexao

    private fun connectionLoop(cfg: MiningConfig) {
        var backoffMs = 2_000L
        while (running.get() && config === cfg) {
            _stats.update { it.copy(connection = ConnectionState.CONNECTING) }
            val c = StratumClient(
                host = cfg.host, port = cfg.port, useTls = cfg.tls,
                username = cfg.username, password = cfg.password,
                suggestedDifficulty = cfg.suggestedDifficulty,
                listener = listener
            )
            client = c
            val disconnected = Object()
            disconnectSignal = disconnected
            try {
                c.connect()
                backoffMs = 2_000L
                // aguarda ate a desconexao
                synchronized(disconnected) {
                    while (running.get() && !c.isClosed) disconnected.wait(1_000)
                }
            } catch (e: IOException) {
                val msg = e.message ?: e.javaClass.simpleName
                log("falha ao conectar: $msg")
                _stats.update { it.copy(connection = ConnectionState.ERROR, lastError = msg) }
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) {
                log("erro inesperado: ${e.message}")
                _stats.update { it.copy(connection = ConnectionState.ERROR, lastError = e.message) }
            } finally {
                if (!c.isClosed) c.close("reconectando")
            }
            currentJob = null
            jobGeneration.incrementAndGet()
            if (!running.get()) return
            log("nova tentativa em ${backoffMs / 1000}s")
            try { Thread.sleep(backoffMs) } catch (e: InterruptedException) { return }
            backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
        }
    }

    @Volatile private var disconnectSignal: Object? = null

    private val listener = object : StratumClient.Listener {
        override fun onConnected(client: StratumClient) {
            log("conectado; assinando trabalho")
            _stats.update { it.copy(connection = ConnectionState.CONNECTED, lastError = null) }
        }

        override fun onSubscribed(client: StratumClient, extranonce1: ByteArray, extranonce2Size: Int) {
            log("subscribe ok (extranonce1=${Hash.hex(extranonce1)}, en2=${extranonce2Size}B)")
        }

        override fun onAuthorized(client: StratumClient, ok: Boolean, message: String?) {
            if (ok) {
                log("worker autorizado")
                _stats.update { it.copy(connection = ConnectionState.AUTHORIZED) }
            } else {
                log("autorizacao recusada: ${message ?: "verifique o endereco/usuario"}")
                _stats.update { it.copy(connection = ConnectionState.ERROR, lastError = "autorizacao recusada: ${message ?: ""}") }
            }
        }

        override fun onDifficulty(client: StratumClient, difficulty: Double) {
            shareTarget = Target.fromDifficulty(difficulty)
            log("dificuldade do share: ${formatDifficulty(difficulty)}")
            _stats.update { it.copy(shareDifficulty = difficulty) }
        }

        override fun onJob(client: StratumClient, job: StratumJob) {
            if (client !== this@MiningEngine.client) return
            currentJob = job
            jobGeneration.incrementAndGet()
            val h = job.height
            log("novo job ${job.jobId}${if (h != null) " (bloco #$h)" else ""}${if (job.cleanJobs) " [limpar]" else ""}")
            _stats.update {
                it.copy(
                    currentJobId = job.jobId,
                    blockHeight = h,
                    prevHash = job.prevHashDisplay,
                    networkDifficulty = job.networkDifficulty
                )
            }
        }

        override fun onShareResult(client: StratumClient, submitId: Int, accepted: Boolean, message: String?) {
            val diff = submitted.remove(submitId)
            if (accepted) {
                log("share ACEITO${if (diff != null) " (dif. ${formatDifficulty(diff)})" else ""}")
                _stats.update { it.copy(sharesAccepted = it.sharesAccepted + 1) }
            } else {
                log("share REJEITADO: ${message ?: "sem motivo"}")
                _stats.update { it.copy(sharesRejected = it.sharesRejected + 1) }
            }
        }

        override fun onDisconnected(client: StratumClient, reason: String) {
            if (client !== this@MiningEngine.client) return
            log("desconectado: $reason")
            _stats.update { it.copy(connection = if (running.get()) ConnectionState.CONNECTING else ConnectionState.DISCONNECTED) }
            disconnectSignal?.let { s -> synchronized(s) { s.notifyAll() } }
        }

        override fun onLog(client: StratumClient, line: String) = log(line)
    }

    // ------------------------------------------------------------------ workers

    private fun startWorkers(cfg: MiningConfig) {
        val list = ArrayList<Worker>()
        val total = cfg.maxThreads.coerceAtLeast(1)
        for (i in 0 until total) {
            val w = Worker(i, total)
            w.start()
            list.add(w)
        }
        workers = list
    }

    private inner class Worker(private val id: Int, private val count: Int) :
        Thread("miner-$id") {

        init {
            isDaemon = true
            priority = MIN_PRIORITY + 1
        }

        private val sha = Sha256Miner()

        override fun run() {
            var en2Counter = id.toLong()
            var lastJob: StratumJob? = null
            while (running.get()) {
                val job = currentJob
                if (job == null || paused.get() || id >= activeThreads || intensityNow <= 0) {
                    try { sleep(200) } catch (e: InterruptedException) { return }
                    continue
                }
                if (job !== lastJob) {
                    lastJob = job
                    en2Counter = id.toLong()
                }
                val generation = jobGeneration.get()
                val en2 = job.extranonce2For(en2Counter)
                val header = job.buildHeader(en2)
                val words = Sha256Miner.headerWords(header)
                val mid = Sha256Miner.midstate(words)
                val t0 = words[16]; val t1 = words[17]; val t2 = words[18]

                var nonce = 0
                var exhausted = false
                var batchStart = System.nanoTime()
                while (running.get()) {
                    // lote de 4096 hashes
                    var i = 0
                    while (i < 4096) {
                        val last = sha.hash(mid, t0, t1, t2, nonce)
                        if ((last and 0xFFFF) == 0) {
                            val h = sha.lastHash()
                            if (Target.meets(h, shareTarget)) submitShare(job, en2, nonce, h)
                        }
                        nonce++
                        i++
                        if (nonce == 0) { exhausted = true; break }
                    }
                    hashCounter.addAndGet(i.toLong())
                    if (exhausted || generation != jobGeneration.get() || paused.get() || id >= activeThreads) break
                    val intensity = intensityNow
                    if (intensity <= 0) break
                    if (intensity < 100) {
                        val now = System.nanoTime()
                        val busyMs = (now - batchStart) / 1_000_000
                        val idleMs = busyMs * (100 - intensity) / intensity
                        if (idleMs > 0) {
                            try { sleep(idleMs) } catch (e: InterruptedException) { return }
                        }
                        batchStart = System.nanoTime()
                    }
                }
                // proximo extranonce2 deste worker (espaco disjunto entre threads)
                en2Counter += count
            }
        }
    }

    private fun submitShare(job: StratumJob, en2: ByteArray, nonceWord: Int, hash: ByteArray) {
        val c = client ?: return
        val diff = Target.difficultyOf(hash)
        val nonceHex = Sha256Miner.nonceHex(nonceWord)
        val id = c.submit(job.jobId, Hash.hex(en2), job.ntimeHex, nonceHex)
        submitted[id] = diff
        val isBlock = Target.meets(hash, job.networkTarget)
        log("share encontrado (dif. ${formatDifficulty(diff)}) nonce=$nonceHex")
        _stats.update {
            it.copy(
                sharesSubmitted = it.sharesSubmitted + 1,
                bestShareDifficulty = maxOf(it.bestShareDifficulty, diff),
                blocksFound = if (isBlock) it.blocksFound + 1 else it.blocksFound
            )
        }
        if (isBlock) {
            log("*** BLOCO ENCONTRADO! hash ${Target.displayHex(hash)} ***")
        }
    }

    // ------------------------------------------------------------------ estatisticas

    private fun statsLoop() {
        var lastCount = 0L
        var lastTime = System.nanoTime()
        var ema = 0.0
        while (running.get()) {
            try { Thread.sleep(1_000) } catch (e: InterruptedException) { return }
            val now = System.nanoTime()
            val count = hashCounter.get()
            val dt = (now - lastTime) / 1e9
            val rate = if (dt > 0) (count - lastCount) / dt else 0.0
            ema = if (ema == 0.0) rate else ema * 0.7 + rate * 0.3
            lastCount = count
            lastTime = now
            val st = _stats.value
            val elapsed = (System.currentTimeMillis() - st.startedAt) / 1000.0
            _stats.update {
                it.copy(
                    hashrate = if (paused.get()) 0.0 else ema,
                    hashrateAverage = if (elapsed > 0) count / elapsed else 0.0,
                    totalHashes = count
                )
            }
        }
    }

    private fun log(line: String) {
        val stamped = "[${timeFmt.format(System.currentTimeMillis())}] $line"
        val snapshot: List<String>
        synchronized(logLines) {
            logLines.addLast(stamped)
            while (logLines.size > MAX_LOG) logLines.removeFirst()
            snapshot = logLines.toList()
        }
        _stats.update { it.copy(log = snapshot) }
    }

    companion object {
        const val MAX_LOG = 80

        fun formatHashrate(hs: Double): String = when {
            hs >= 1e12 -> String.format(Locale.US, "%.2f TH/s", hs / 1e12)
            hs >= 1e9 -> String.format(Locale.US, "%.2f GH/s", hs / 1e9)
            hs >= 1e6 -> String.format(Locale.US, "%.2f MH/s", hs / 1e6)
            hs >= 1e3 -> String.format(Locale.US, "%.1f kH/s", hs / 1e3)
            else -> String.format(Locale.US, "%.0f H/s", hs)
        }

        fun formatDifficulty(d: Double): String = when {
            d >= 1e12 -> String.format(Locale.US, "%.2fT", d / 1e12)
            d >= 1e9 -> String.format(Locale.US, "%.2fG", d / 1e9)
            d >= 1e6 -> String.format(Locale.US, "%.2fM", d / 1e6)
            d >= 1e3 -> String.format(Locale.US, "%.2fk", d / 1e3)
            d >= 1 -> String.format(Locale.US, "%.2f", d)
            else -> String.format(Locale.US, "%.4f", d)
        }
    }
}

/** Instancia unica compartilhada entre a UI e o servico em primeiro plano. */
object Miner {
    val engine: MiningEngine by lazy { MiningEngine() }
}
