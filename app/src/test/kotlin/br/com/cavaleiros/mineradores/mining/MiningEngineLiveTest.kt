package br.com.cavaleiros.mineradores.mining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Roda o motor completo contra um pool real por alguns minutos (so com
 * -Dstratum.live=true). Verifica conexao, recebimento de job, hashrate e, se
 * algum share for encontrado no tempo disponivel, que ele e ACEITO pelo pool -
 * a prova definitiva de que cabecalho, extranonce2 e nonce estao corretos.
 */
class MiningEngineLiveTest {

    private fun live(): Boolean = System.getProperty("stratum.live", "false") == "true"

    @Test
    fun minesAgainstPublicPoolAndSharesAreAccepted() {
        assumeTrue(live())
        val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(1, 12)
        val engine = MiningEngine()
        engine.start(
            MiningConfig(
                host = "public-pool.io", port = 21496, tls = false,
                username = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu.cavaleirostest",
                password = "x", threads = threads, intensity = 100,
                suggestedDifficulty = 0.001, label = "teste"
            )
        )
        val start = System.currentTimeMillis()
        val maxMs = 300_000L
        var snapshot = engine.stats.value
        try {
            while (System.currentTimeMillis() - start < maxMs) {
                Thread.sleep(5_000)
                snapshot = engine.stats.value
                val elapsed = (System.currentTimeMillis() - start) / 1000
                println(
                    "[${elapsed}s] ${snapshot.connection} hashrate=${MiningEngine.formatHashrate(snapshot.hashrate)} " +
                        "hashes=${snapshot.totalHashes} enviados=${snapshot.sharesSubmitted} aceitos=${snapshot.sharesAccepted} " +
                        "rejeitados=${snapshot.sharesRejected} job=${snapshot.currentJobId} dif=${snapshot.shareDifficulty} bloco=${snapshot.blockHeight}"
                )
                if (snapshot.sharesAccepted + snapshot.sharesRejected >= 2) break
                if (snapshot.connection == ConnectionState.ERROR && elapsed > 30) break
            }
        } finally {
            snapshot = engine.stats.value
            engine.stop()
        }
        println("---- log do motor ----")
        println(snapshot.log.joinToString("\n"))

        assertEquals("conexao deveria estar autorizada", ConnectionState.AUTHORIZED, snapshot.connection)
        assertNotNull("nenhum job recebido", snapshot.currentJobId)
        assertTrue("nenhum hash calculado", snapshot.totalHashes > 1_000_000)
        assertTrue("hashrate medio zero", snapshot.hashrateAverage > 0)
        assertEquals("shares rejeitados pelo pool", 0, snapshot.sharesRejected)
        if (snapshot.sharesSubmitted == 0) {
            println("AVISO: nenhum share encontrado em ${maxMs / 1000}s com ${threads} threads (dificuldade ${snapshot.shareDifficulty}); " +
                "isso e esperado em hardware lento e nao indica erro.")
        } else {
            assertTrue("shares enviados mas nenhum aceito", snapshot.sharesAccepted > 0)
        }
    }
}
