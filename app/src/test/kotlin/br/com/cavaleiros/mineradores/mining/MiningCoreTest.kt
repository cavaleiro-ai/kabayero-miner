package br.com.cavaleiros.mineradores.mining

import br.com.cavaleiros.mineradores.wallet.Hash
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.random.Random

class Sha256MinerTest {
    private val genesisHeader = Hash.unhex(
        "0100000000000000000000000000000000000000000000000000000000000000000000003ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a29ab5f49ffff001d1dac2b7c"
    )
    private val block125552Header = Hash.unhex(
        "0100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a14695"
    )

    private fun mine(header: ByteArray): ByteArray {
        val words = Sha256Miner.headerWords(header)
        val mid = Sha256Miner.midstate(words)
        val miner = Sha256Miner()
        miner.hash(mid, words[16], words[17], words[18], words[19])
        return miner.lastHash()
    }

    @Test
    fun genesisBlockHash() {
        val h = mine(genesisHeader)
        assertEquals("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f", Target.displayHex(h))
        assertTrue(Target.meets(h, Target.fromBits(0x1d00ffff)))
        assertEquals("7c2bac1d", Sha256Miner.nonceHex(Sha256Miner.headerWords(genesisHeader)[19]))
    }

    @Test
    fun block125552Hash() {
        val h = mine(block125552Header)
        assertEquals("00000000000000001e8d6829a8a21adc5d38d0a473b144b6765798e61f98bd1d", Target.displayHex(h))
        assertTrue(Target.meets(h, Target.fromBits(0x1a44b9f2)))
        // bytes do nonce no cabecalho: 42 a1 46 95 (little-endian) -> valor 0x9546a142 -> hex enviado ao pool
        assertEquals("9546a142", Sha256Miner.nonceHex(Sha256Miner.headerWords(block125552Header)[19]))
    }

    @Test
    fun matchesMessageDigestForRandomHeaders() {
        val rnd = Random(42)
        val md = MessageDigest.getInstance("SHA-256")
        val miner = Sha256Miner()
        repeat(500) {
            val header = ByteArray(80).also { rnd.nextBytes(it) }
            val words = Sha256Miner.headerWords(header)
            val mid = Sha256Miner.midstate(words)
            val last = miner.hash(mid, words[16], words[17], words[18], words[19])
            val expected = md.digest(md.digest(header))
            assertArrayEquals(expected, miner.lastHash())
            // ultimos 4 bytes do hash = palavra s2[7] big-endian
            val lastWordExpected = ((expected[28].toInt() and 0xFF) shl 24) or ((expected[29].toInt() and 0xFF) shl 16) or
                ((expected[30].toInt() and 0xFF) shl 8) or (expected[31].toInt() and 0xFF)
            assertEquals(lastWordExpected, last)
        }
    }

    @Test
    fun quickFilterIsNecessaryConditionForDiff1() {
        // Para um hash atingir dificuldade >= 2^-16, os 16 bits baixos da ultima palavra devem ser zero.
        val h = mine(genesisHeader)
        val words = Sha256Miner.headerWords(genesisHeader)
        val miner = Sha256Miner()
        val last = miner.hash(Sha256Miner.midstate(words), words[16], words[17], words[18], words[19])
        assertEquals(0, last and 0xFFFF)
        assertTrue(Target.difficultyOf(h) >= 1.0)
    }
}

class TargetTest {
    @Test
    fun diff1Bits() {
        assertEquals(Target.DIFF1, Target.fromBits(0x1d00ffff))
        assertEquals(Target.DIFF1, Target.fromDifficulty(1.0))
        assertEquals(1.0, Target.difficultyFromBits(0x1d00ffff), 1e-9)
    }

    @Test
    fun fractionalAndLargeDifficulty() {
        val t = Target.fromDifficulty(0.5)
        assertEquals(Target.DIFF1.shiftLeft(1), t)
        val t2 = Target.fromDifficulty(4096.0)
        assertEquals(Target.DIFF1.shiftRight(12), t2)
        // dificuldade de rede (bloco 125552, bits 0x1a44b9f2) ~ 244112
        val d = Target.difficultyFromBits(0x1a44b9f2)
        assertTrue(d > 244_000 && d < 245_000)
    }

    @Test
    fun hashValueOrdering() {
        val zero = ByteArray(32)
        assertEquals(BigInteger.ZERO, Target.hashToValue(zero))
        val one = ByteArray(32).also { it[0] = 1 }
        assertEquals(BigInteger.ONE, Target.hashToValue(one))
        val big = ByteArray(32).also { it[31] = 1 }
        assertEquals(BigInteger.ONE.shiftLeft(248), Target.hashToValue(big))
        assertFalse(Target.meets(big, Target.DIFF1))
    }
}

class StratumJobTest {
    /** Job sintetico com coinbase valida (BIP34 altura 840000) e um ramo merkle. */
    private fun job(branches: List<ByteArray> = emptyList()) = StratumJob(
        jobId = "abc1",
        // prevhash em formato stratum: palavras de 4 bytes invertidas em relacao ao cabecalho
        prevHashHex = "0000000000000000000215e8ba5e9fe1b3b6a8ae7a0c6d1f3e2d4c5b6a798877",
        // version + 1 input + prevout nulo + script len 0x20 + push de 3 bytes com altura 840000 (0x0CD140 LE = 40 d1 0c)
        coinb1 = Hash.unhex("01000000" + "01" + "00".repeat(32) + "ffffffff" + "20" + "0340d10c"),
        // push "cavaleiros" + sequence + 1 saida de 50 BTC para P2WPKH nulo + locktime
        coinb2 = Hash.unhex("0a636176616c6569726f73" + "ffffffff" + "01" + "00f2052a01000000" + "16" + "0014" + "00".repeat(20) + "00000000"),
        merkleBranches = branches,
        versionHex = "20000000",
        nbitsHex = "17034219",
        ntimeHex = "66200000",
        cleanJobs = true,
        extranonce1 = Hash.unhex("deadbeef"),
        extranonce2Size = 4
    )

    @Test
    fun headerLayoutAndByteOrder() {
        val j = job()
        val en2 = j.extranonce2For(7)
        assertEquals("00000007", Hash.hex(en2))
        val header = j.buildHeader(en2)
        assertEquals(80, header.size)
        // version 0x20000000 little-endian
        assertEquals("00000020", Hash.hex(header.copyOfRange(0, 4)))
        // prevhash: primeira palavra stratum 00000000 -> cabecalho 00000000; ultima palavra 6a798877 -> 7788796a
        assertEquals("7788796a", Hash.hex(header.copyOfRange(32, 36)))
        // exibicao = bytes do cabecalho invertidos: comeca pela ultima palavra invertida (6a798877) e termina nos zeros
        assertEquals("6a7988773e2d4c5b7a0c6d1fb3b6a8aeba5e9fe1000215e80000000000000000", j.prevHashDisplay)
        assertEquals(64, j.prevHashDisplay.length)
        // ntime / nbits little-endian
        assertEquals("00002066", Hash.hex(header.copyOfRange(68, 72)))
        assertEquals("19420317", Hash.hex(header.copyOfRange(72, 76)))
        assertEquals("00000000", Hash.hex(header.copyOfRange(76, 80)))
        // merkle root sem ramos = sha256d(coinbase)
        assertArrayEquals(Hash.sha256d(j.coinbase(en2)), header.copyOfRange(36, 68))
        assertEquals(840000, j.height)
    }

    @Test
    fun merkleWithBranches() {
        val b1 = ByteArray(32) { 1 }
        val b2 = ByteArray(32) { 2 }
        val j = job(listOf(b1, b2))
        val en2 = j.extranonce2For(0)
        var expected = Hash.sha256d(j.coinbase(en2))
        expected = Hash.sha256d(expected + b1)
        expected = Hash.sha256d(expected + b2)
        assertArrayEquals(expected, j.merkleRoot(en2))
    }

    @Test
    fun coinbaseConcatenation() {
        val j = job()
        val cb = j.coinbase(j.extranonce2For(0x01020304))
        val hex = Hash.hex(cb)
        assertTrue(hex.startsWith(Hash.hex(j.coinb1)))
        assertTrue(hex.contains("deadbeef01020304"))
        assertTrue(hex.endsWith(Hash.hex(j.coinb2)))
    }

    @Test
    fun networkTargetFromBits() {
        val j = job()
        assertEquals(Target.fromBits(0x17034219), j.networkTarget)
        assertTrue(j.networkDifficulty > 1e13)
        assertNotNull(j.prevHashDisplay)
    }

    @Test
    fun minerHashEqualsReferenceForJobHeader() {
        val j = job(listOf(ByteArray(32) { 9 }))
        val header = j.buildHeader(j.extranonce2For(3))
        val words = Sha256Miner.headerWords(header)
        val miner = Sha256Miner()
        val mid = Sha256Miner.midstate(words)
        val nonceWord = 0x11223344
        miner.hash(mid, words[16], words[17], words[18], nonceWord)
        StratumJob.putLe32(header, 76, Integer.reverseBytes(nonceWord))
        assertArrayEquals(Hash.sha256d(header), miner.lastHash())
        assertEquals("44332211", Sha256Miner.nonceHex(nonceWord))
    }
}
