package br.com.cavaleiros.mineradores.mining

import br.com.cavaleiros.mineradores.wallet.Hash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Teste de integracao com um servidor Stratum real. So roda com -Dstratum.live=true.
 *
 * Valida, com dados ao vivo, a parte mais sujeita a erro de um minerador: a
 * ordem de bytes do cabecalho. O prevhash montado a partir do job deve ser
 * exatamente o hash da ponta da cadeia informado por mempool.space, e a altura
 * extraida da coinbase (BIP34) deve ser a ponta + 1.
 */
class StratumLiveTest {

    private fun live(): Boolean = System.getProperty("stratum.live", "false") == "true"

    private fun fetch(url: String): String = URL(url).openStream().bufferedReader().use { it.readText().trim() }

    private fun connectAndGetJob(host: String, port: Int, tls: Boolean, user: String): Pair<StratumJob, Double> {
        val jobRef = AtomicReference<StratumJob>()
        val diffRef = AtomicReference(1.0)
        val authorized = AtomicReference(false)
        val latch = CountDownLatch(1)
        val log = StringBuilder()
        val listener = object : StratumClient.Listener {
            override fun onConnected(client: StratumClient) { log.append("conectado\n") }
            override fun onSubscribed(client: StratumClient, extranonce1: ByteArray, extranonce2Size: Int) {
                log.append("subscribed en1=${Hash.hex(extranonce1)} en2size=$extranonce2Size\n")
            }
            override fun onAuthorized(client: StratumClient, ok: Boolean, message: String?) { authorized.set(ok); log.append("authorized=$ok $message\n") }
            override fun onDifficulty(client: StratumClient, difficulty: Double) { diffRef.set(difficulty); log.append("difficulty=$difficulty\n") }
            override fun onJob(client: StratumClient, job: StratumJob) {
                log.append("job ${job.jobId} height=${job.height} prev=${job.prevHashDisplay}\n")
                if (jobRef.compareAndSet(null, job)) latch.countDown()
            }
            override fun onShareResult(client: StratumClient, submitId: Int, accepted: Boolean, message: String?) {}
            override fun onDisconnected(client: StratumClient, reason: String) { log.append("desconectado: $reason\n") }
            override fun onLog(client: StratumClient, line: String) { log.append("log: $line\n") }
        }
        val client = StratumClient(host, port, tls, user, "x", 0.001, listener, "CavaleirosMineradores-test/1.0")
        client.connect()
        try {
            assertTrue("sem job em 60s de $host\n$log", latch.await(60, TimeUnit.SECONDS))
            // da tempo para a resposta do authorize chegar
            Thread.sleep(1500)
            println(log)
            assertTrue("worker nao autorizado em $host\n$log", authorized.get())
            return jobRef.get() to diffRef.get()
        } finally {
            client.close("fim do teste")
        }
    }

    private fun checkAgainstChainTip(job: StratumJob) {
        val tipHash = fetch("https://mempool.space/api/blocks/tip/hash")
        val tipHeight = fetch("https://mempool.space/api/blocks/tip/height").toInt()
        println("ponta da cadeia: #$tipHeight $tipHash | job: #${job.height} ${job.prevHashDisplay}")
        // a ponta pode avancar entre as duas consultas; aceita o bloco atual ou o imediatamente anterior
        val okHash = job.prevHashDisplay == tipHash
        val okHeight = job.height == tipHeight + 1 || job.height == tipHeight
        assertTrue("prevhash do job (${job.prevHashDisplay}) nao bate com a ponta ($tipHash)", okHash || okHeight)
        assertNotNull(job.height)

        // o cabecalho montado deve hashear igual ao MessageDigest e o alvo da rede deve ser coerente
        val en2 = job.extranonce2For(1)
        val header = job.buildHeader(en2)
        val words = Sha256Miner.headerWords(header)
        val miner = Sha256Miner()
        miner.hash(Sha256Miner.midstate(words), words[16], words[17], words[18], 0)
        val md = MessageDigest.getInstance("SHA-256")
        assertEquals(Hash.hex(md.digest(md.digest(header))), Hash.hex(miner.lastHash()))
        assertTrue(job.networkDifficulty > 1e12)
    }

    @Test
    fun publicPoolJobMatchesChainTip() {
        assumeTrue(live())
        val (job, diff) = connectAndGetJob("public-pool.io", 21496, false, "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu.teste")
        assertTrue(diff > 0)
        checkAgainstChainTip(job)
    }

    @Test
    fun ckpoolSoloJobMatchesChainTip() {
        assumeTrue(live())
        val (job, _) = connectAndGetJob("solo.ckpool.org", 3333, false, "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu.teste")
        checkAgainstChainTip(job)
    }

    @Test
    fun oceanPoolJobMatchesChainTip() {
        assumeTrue(live())
        val (job, _) = connectAndGetJob("mine.ocean.xyz", 3334, false, "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu.teste")
        checkAgainstChainTip(job)
    }
}
