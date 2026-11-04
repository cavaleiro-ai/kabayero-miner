package br.com.cavaleiros.mineradores.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class EcdsaTest {

    private fun key(hex: String): ByteArray {
        val raw = Hash.unhex(hex)
        return ByteArray(32 - raw.size) + raw
    }

    private fun sha256(msg: String) = Hash.sha256(msg.toByteArray(Charsets.US_ASCII))

    /** Vetores RFC 6979 para secp256k1 (mensagem com SHA-256, S baixo) usados por trezor-crypto e bitcoinjs. */
    @Test
    fun rfc6979VectorsSecp256k1() {
        val cases = listOf(
            Triple("01", "Satoshi Nakamoto",
                "934b1ea10a4b3c1757e2b0c017d0b6143ce3c9a7e6a4a49860d7a6ab210ee3d8" to "2442ce9d2b916064108014783e923ec36b49743e2ffa1c4496f01a512aafd9e5"),
            Triple("01", "All those moments will be lost in time, like tears in rain. Time to die...",
                "8600dbd41e348fe5c9465ab92d23e3db8b98b873beecd930736488696438cb6b" to "547fe64427496db33bf66019dacbf0039c04199abb0122918601db38a72cfc21"),
            Triple("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "Satoshi Nakamoto",
                "fd567d121db66e382991534ada77a6bd3106f0a1098c231e47993447cd6af2d0" to "6b39cd0eb1bc8603e159ef5c20a5c8ad685a45b06ce9bebed3f153d10d93bed5"),
            Triple("f8b8af8ce3c7cca5e300d33939540c10d45ce001b8f252bfbc57ba0342904181", "Alan Turing",
                "7063ae83e7f62bbb171798131b4a0564b956930092b33b07b395615d9ec7e15c" to "58dfcc1e00a35e1572f366ffe34ba0fc47db1e7189759b9fb233c5b05ab388ea")
        )
        for ((k, msg, expected) in cases) {
            val sig = Ecdsa.sign(key(k), sha256(msg))
            assertEquals("r para '$msg'", expected.first, sig.r.toString(16).padStart(64, '0'))
            assertEquals("s para '$msg'", expected.second, sig.s.toString(16).padStart(64, '0'))
            val pub = Secp256k1.publicKey(key(k))
            assertTrue(Ecdsa.verify(pub, sha256(msg), sig))
        }
    }

    @Test
    fun derEncodingIsMinimalAndRoundTrips() {
        val sig = Ecdsa.sign(key("01"), sha256("Satoshi Nakamoto"))
        val der = sig.toDer()
        assertEquals(0x30, der[0].toInt())
        assertEquals(der.size - 2, der[1].toInt())
        val parsed = Ecdsa.parseDer(der)!!
        assertEquals(sig.r, parsed.r)
        assertEquals(sig.s, parsed.s)
        // r com bit alto precisa do 0x00 de prefixo (BIP66)
        assertEquals("3045022100934b1ea10a4b3c1757e2b0c017d0b6143ce3c9a7e6a4a49860d7a6ab210ee3d802202442ce9d2b916064108014783e923ec36b49743e2ffa1c4496f01a512aafd9e5", Hash.hex(der))
    }

    @Test
    fun lowSIsEnforced() {
        val half = Secp256k1.N.shiftRight(1)
        for (i in 1..20) {
            val sig = Ecdsa.sign(key("0$i".takeLast(2)), sha256("msg $i"))
            assertTrue(sig.s <= half)
        }
    }

    @Test
    fun verifyRejectsTamperedSignature() {
        val priv = key("02")
        val pub = Secp256k1.publicKey(priv)
        val h = sha256("hello")
        val sig = Ecdsa.sign(priv, h)
        assertTrue(Ecdsa.verify(pub, h, sig))
        assertFalse(Ecdsa.verify(pub, sha256("hellp"), sig))
        assertFalse(Ecdsa.verify(pub, h, Ecdsa.Signature(sig.r, sig.s.add(BigInteger.ONE))))
        assertFalse(Ecdsa.verify(Secp256k1.publicKey(key("03")), h, sig))
    }
}
