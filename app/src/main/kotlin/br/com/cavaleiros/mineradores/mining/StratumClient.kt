package br.com.cavaleiros.mineradores.mining

import br.com.cavaleiros.mineradores.wallet.Hash
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Cliente Stratum V1 (JSON-RPC sobre TCP, uma mensagem por linha).
 *
 * Suporta TLS com verificacao de certificado e hostname pelo trust store do
 * sistema. Os callbacks em [Listener] sao chamados na thread de leitura.
 */
class StratumClient(
    private val host: String,
    private val port: Int,
    private val useTls: Boolean,
    private val username: String,
    private val password: String,
    private val suggestedDifficulty: Double?,
    private val listener: Listener,
    private val userAgent: String = "CavaleirosMineradores/1.0"
) {
    interface Listener {
        fun onConnected(client: StratumClient)
        fun onSubscribed(client: StratumClient, extranonce1: ByteArray, extranonce2Size: Int)
        fun onAuthorized(client: StratumClient, ok: Boolean, message: String?)
        fun onDifficulty(client: StratumClient, difficulty: Double)
        fun onJob(client: StratumClient, job: StratumJob)
        fun onShareResult(client: StratumClient, submitId: Int, accepted: Boolean, message: String?)
        fun onDisconnected(client: StratumClient, reason: String)
        fun onLog(client: StratumClient, line: String)
    }

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var readerThread: Thread? = null
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, String>()
    private val closed = AtomicBoolean(false)
    private val writeLock = Any()

    @Volatile var extranonce1: ByteArray = ByteArray(0); private set
    @Volatile var extranonce2Size: Int = 4; private set
    @Volatile var difficulty: Double = 1.0; private set
    @Volatile var authorized: Boolean = false; private set

    val isClosed: Boolean get() = closed.get()

    /** Conecta e inicia a thread de leitura. Lanca IOException em falha de conexao. */
    @Throws(IOException::class)
    fun connect() {
        val plain = openTcpSocket()
        plain.soTimeout = READ_TIMEOUT_MS

        val sock: Socket = if (useTls) {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(plain, host, port, true) as SSLSocket
            ssl.useClientMode = true
            ssl.startHandshake()
            val verifier = HttpsURLConnection.getDefaultHostnameVerifier()
            if (!verifier.verify(host, ssl.session)) {
                ssl.close()
                throw IOException("Certificado TLS nao corresponde ao host $host")
            }
            ssl
        } else plain

        socket = sock
        writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8))
        listener.onConnected(this)

        val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8), 65536)
        readerThread = Thread({ readLoop(reader) }, "stratum-reader").apply {
            isDaemon = true
            start()
        }

        send("mining.subscribe", JSONArray().put(userAgent))
    }

    /**
     * Resolve o host e tenta cada endereco em ordem (IPv4 primeiro, depois IPv6).
     * Muitas operadoras moveis entregam IPv6 cuja rota ate o pool falha enquanto o
     * IPv4 funciona; sem este fallback a conexao morreria no primeiro endereco.
     */
    @Throws(IOException::class)
    private fun openTcpSocket(): Socket {
        val addresses = try {
            InetAddress.getAllByName(host).toList()
        } catch (e: UnknownHostException) {
            throw IOException("nao foi possivel resolver $host")
        }
        val ordered = addresses.sortedBy { if (it is Inet4Address) 0 else 1 }
        var lastError: IOException? = null
        for (addr in ordered) {
            val s = Socket()
            try {
                s.tcpNoDelay = true
                s.keepAlive = true
                s.connect(InetSocketAddress(addr, port), CONNECT_TIMEOUT_MS)
                listener.onLog(this, "tcp ok -> ${addr.hostAddress}:$port")
                return s
            } catch (e: IOException) {
                lastError = e
                try { s.close() } catch (_: IOException) {}
                listener.onLog(this, "falha em ${addr.hostAddress}: ${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName}")
            }
        }
        throw lastError ?: IOException("nenhum endereco para $host")
    }

    fun close(reason: String = "fechado pelo usuario") {
        if (!closed.compareAndSet(false, true)) return
        try { socket?.close() } catch (_: IOException) {}
        listener.onDisconnected(this, reason)
    }

    /** Envia mining.submit e devolve o id da requisicao. */
    fun submit(jobId: String, extranonce2Hex: String, ntimeHex: String, nonceHex: String): Int {
        val params = JSONArray().put(username).put(jobId).put(extranonce2Hex).put(ntimeHex).put(nonceHex)
        return send("mining.submit", params)
    }

    private fun send(method: String, params: JSONArray): Int {
        val id = nextId.getAndIncrement()
        val msg = JSONObject().put("id", id).put("method", method).put("params", params)
        pending[id] = method
        try {
            synchronized(writeLock) {
                val w = writer ?: throw IOException("sem conexao")
                w.write(msg.toString())
                w.write("\n")
                w.flush()
            }
        } catch (e: IOException) {
            pending.remove(id)
            close("erro de escrita: ${e.message}")
        }
        return id
    }

    private fun readLoop(reader: BufferedReader) {
        var reason = "conexao encerrada pelo servidor"
        try {
            while (!closed.get()) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                try {
                    handleLine(line)
                } catch (e: Exception) {
                    listener.onLog(this, "mensagem ignorada: ${e.message}")
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            reason = "sem mensagens do pool por ${READ_TIMEOUT_MS / 60000} min"
        } catch (e: IOException) {
            if (!closed.get()) reason = "erro de rede: ${e.message}"
        }
        if (closed.compareAndSet(false, true)) {
            try { socket?.close() } catch (_: IOException) {}
            listener.onDisconnected(this, reason)
        }
    }

    private fun handleLine(line: String) {
        val obj = JSONObject(line)
        val method = obj.optString("method", "")
        if (method.isNotEmpty()) {
            handleNotification(method, obj.optJSONArray("params") ?: JSONArray(), obj)
            return
        }
        val id = obj.optInt("id", -1)
        val reqMethod = pending.remove(id)
        val error = if (obj.isNull("error")) null else obj.opt("error")
        val errorMsg = describeError(error)
        when (reqMethod) {
            "mining.subscribe" -> handleSubscribeResult(obj.opt("result"), errorMsg)
            "mining.authorize" -> {
                val ok = errorMsg == null && obj.optBoolean("result", false)
                authorized = ok
                listener.onAuthorized(this, ok, errorMsg)
                // alguns pools so consideram a sugestao depois da autorizacao: reenvia
                if (ok) suggestedDifficulty?.let { send("mining.suggest_difficulty", JSONArray().put(it)) }
            }
            "mining.submit" -> {
                val accepted = errorMsg == null && obj.optBoolean("result", false)
                listener.onShareResult(this, id, accepted, errorMsg)
            }
            "mining.suggest_difficulty" -> {
                if (errorMsg != null) listener.onLog(this, "pool nao aceita sugestao de dificuldade ($errorMsg)")
            }
            null -> listener.onLog(this, "resposta sem requisicao correspondente (id=$id)")
            else -> {}
        }
    }

    private fun handleSubscribeResult(result: Any?, errorMsg: String?) {
        if (errorMsg != null || result !is JSONArray) {
            close("falha no subscribe: ${errorMsg ?: "resposta invalida"}")
            return
        }
        // result = [subscriptions, extranonce1, extranonce2_size]
        val en1Hex = result.optString(1, "")
        val en2Size = result.optInt(2, 4)
        extranonce1 = if (en1Hex.isEmpty()) ByteArray(0) else Hash.unhex(en1Hex)
        extranonce2Size = en2Size
        listener.onSubscribed(this, extranonce1, extranonce2Size)
        send("mining.authorize", JSONArray().put(username).put(password))
        suggestedDifficulty?.let { send("mining.suggest_difficulty", JSONArray().put(it)) }
    }

    private fun handleNotification(method: String, params: JSONArray, raw: JSONObject) {
        when (method) {
            "mining.notify" -> {
                if (params.length() < 9) throw IllegalArgumentException("notify incompleto")
                val branches = ArrayList<ByteArray>()
                val arr = params.getJSONArray(4)
                for (i in 0 until arr.length()) branches.add(Hash.unhex(arr.getString(i)))
                val job = StratumJob(
                    jobId = params.get(0).toString(),
                    prevHashHex = params.getString(1),
                    coinb1 = Hash.unhex(params.getString(2)),
                    coinb2 = Hash.unhex(params.getString(3)),
                    merkleBranches = branches,
                    versionHex = params.getString(5),
                    nbitsHex = params.getString(6),
                    ntimeHex = params.getString(7),
                    cleanJobs = params.optBoolean(8, true),
                    extranonce1 = extranonce1,
                    extranonce2Size = extranonce2Size
                )
                listener.onJob(this, job)
            }
            "mining.set_difficulty" -> {
                val d = params.optDouble(0, Double.NaN)
                if (!d.isNaN() && d > 0) {
                    difficulty = d
                    listener.onDifficulty(this, d)
                }
            }
            "mining.set_extranonce" -> {
                val en1 = params.optString(0, "")
                if (en1.isNotEmpty()) extranonce1 = Hash.unhex(en1)
                extranonce2Size = params.optInt(1, extranonce2Size)
                listener.onLog(this, "extranonce atualizado pelo pool")
            }
            "client.reconnect" -> {
                listener.onLog(this, "pool solicitou reconexao (${params.optString(0, host)}:${params.optInt(1, port)})")
                close("pool solicitou reconexao")
            }
            "client.show_message" -> listener.onLog(this, "pool: ${params.optString(0, "")}")
            "mining.ping" -> {
                val id = raw.opt("id")
                if (id != null) {
                    synchronized(writeLock) {
                        writer?.write(JSONObject().put("id", id).put("result", "pong").put("error", JSONObject.NULL).toString() + "\n")
                        writer?.flush()
                    }
                }
            }
            else -> listener.onLog(this, "metodo desconhecido: $method")
        }
    }

    private fun describeError(error: Any?): String? {
        if (error == null || error === JSONObject.NULL) return null
        if (error is JSONArray) {
            val code = error.opt(0)
            val msg = error.optString(1, "")
            return if (msg.isNotEmpty()) "$msg (codigo $code)" else "erro $code"
        }
        if (error is JSONObject) {
            return error.optString("message", error.toString())
        }
        return error.toString()
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 5 * 60_000
    }
}
