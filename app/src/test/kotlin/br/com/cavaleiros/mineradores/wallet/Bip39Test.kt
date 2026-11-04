package br.com.cavaleiros.mineradores.wallet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

object TestWordlist {
    val bip39: Bip39 by lazy {
        val f = File("src/main/res/raw/bip39_english.txt")
        require(f.exists()) { "wordlist nao encontrada em ${f.absolutePath}" }
        Bip39.load(f.inputStream())
    }
}

/** Vetores oficiais do BIP39 (trezor/python-mnemonic), passphrase "TREZOR". */
class Bip39Test {
    private val bip39 = TestWordlist.bip39

    private fun check(entropyHex: String, mnemonic: String, seedHex: String) {
        val entropy = Hash.unhex(entropyHex)
        val words = bip39.entropyToMnemonic(entropy)
        assertEquals(mnemonic, words.joinToString(" "))
        assertTrue(bip39.isValid(words))
        assertArrayEquals(entropy, bip39.mnemonicToEntropy(words))
        assertEquals(seedHex, Hash.hex(bip39.toSeed(words, "TREZOR")))
    }

    @Test
    fun wordlistIntegrity() {
        assertEquals(2048, bip39.wordlist.size)
        assertEquals("abandon", bip39.wordlist.first())
        assertEquals("zoo", bip39.wordlist.last())
        assertEquals("2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda",
            Hash.hex(Hash.sha256(bip39.wordlist.joinToString("\n", postfix = "\n").toByteArray())))
    }

    @Test
    fun vectors12Words() {
        check(
            "00000000000000000000000000000000",
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04"
        )
        check(
            "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f",
            "legal winner thank year wave sausage worth useful legal winner thank yellow",
            "2e8905819b8723fe2c1d161860e5ee1830318dbf49a83bd451cfb8440c28bd6fa457fe1296106559a3c80937a1c1069be3a3a5bd381ee6260e8d9739fce1f607"
        )
        check(
            "80808080808080808080808080808080",
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage above",
            "d71de856f81a8acc65e6fc851a38d4d7ec216fd0796d0a6827a3ad6ed5511a30fa280f12eb2e47ed2ac03b5c462a0358d18d69fe4f985ec81778c1b370b652a8"
        )
        check(
            "ffffffffffffffffffffffffffffffff",
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
            "ac27495480225222079d7be181583751e86f571027b0497b5b5d11218e0a8a13332572917f0f8e5a589620c6f15b11c61dee327651a14c34e18231052e48c069"
        )
    }

    @Test
    fun vectors24Words() {
        check(
            "0000000000000000000000000000000000000000000000000000000000000000",
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art",
            "bda85446c68413707090a52022edd26a1c9462295029f2e60cd7c4f2bbd3097170af7a4d73245cafa9c3cca8d561a7c3de6f5d4a10be8ed2a5e608d68f92fcc8"
        )
        check(
            "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f",
            "legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth title",
            "bc09fca1804f7e69da93c2f2028eb238c227f2e9dda30cd63699232578480a4021b146ad717fbb7e451ce9eb835f43620bf5c514db0f8add49f5d121449d3e87"
        )
        check(
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo vote",
            "dd48c104698c30cfe2b6142103248622fb7bb0ff692eebb00089b32d22484e1613912f0a5b694407be899ffd31ed3992c456cdf60f5d4564b8ba3f05a69890ad"
        )
    }

    @Test
    fun invalidChecksumIsRejected() {
        val words = List(12) { "abandon" }
        assertFalse(bip39.isValid(words))
        assertFalse(bip39.isValid(List(24) { "zoo" }))
        assertFalse(bip39.isValid(listOf("abandon", "abandon")))
        assertFalse(bip39.isValid(List(23) { "abandon" } + "palavraquenaoexiste"))
    }

    @Test
    fun randomGenerationRoundTrip() {
        repeat(50) {
            val entropy = bip39.generateEntropy(256)
            assertEquals(32, entropy.size)
            val words = bip39.entropyToMnemonic(entropy)
            assertEquals(24, words.size)
            assertTrue(bip39.isValid(words))
            assertArrayEquals(entropy, bip39.mnemonicToEntropy(words))
        }
    }

    @Test
    fun entropyIsNotRepeated() {
        val seen = HashSet<String>()
        repeat(200) { assertTrue(seen.add(Hash.hex(bip39.generateEntropy(256)))) }
    }

    @Test
    fun wordHelpers() {
        assertTrue(bip39.isWord("Abandon "))
        assertFalse(bip39.isWord("bitcoinzinho"))
        assertEquals(listOf("abandon", "ability", "able", "about", "above", "absent"), bip39.suggestions("ab"))
        assertEquals(listOf("zoo"), bip39.suggestions("zoo"))
        assertEquals(listOf("legal", "winner", "thank"), Bip39.splitWords("  Legal,\nwinner ; THANK "))
        assertNotNull(bip39.mnemonicToEntropy("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" ")))
    }
}
