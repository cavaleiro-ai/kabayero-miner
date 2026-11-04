package br.com.cavaleiros.mineradores.wallet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HashPrimitivesTest {
    @Test
    fun ripemd160KnownVectors() {
        assertEquals("9c1185a5c5e9fc54612808977ee8f548b2258d31", Hash.hex(Ripemd160.digest(ByteArray(0))))
        assertEquals("8eb208f7e05d987a9b044a8e98c6b087f15a0bfc", Hash.hex(Ripemd160.digest("abc".toByteArray())))
        assertEquals("5d0689ef49d2fae572b881b123a85ffa21595f36", Hash.hex(Ripemd160.digest("message digest".toByteArray())))
        assertEquals("f71c27109c692c1b56bbdceb5b9d2865b3708dbc", Hash.hex(Ripemd160.digest("abcdefghijklmnopqrstuvwxyz".toByteArray())))
        assertEquals("9b752e45573d4b39f4dbd3323cab82bf63326bfb", Hash.hex(Ripemd160.digest("1234567890".repeat(8).toByteArray())))
        assertEquals("52783243c1697bdbe16d37f97f68f08325dc1528", Hash.hex(Ripemd160.digest(ByteArray(1_000_000) { 'a'.code.toByte() })))
    }

    @Test
    fun sha256dGenesis() {
        val header = Hash.unhex(
            "0100000000000000000000000000000000000000000000000000000000000000000000003ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a29ab5f49ffff001d1dac2b7c"
        )
        val h = Hash.sha256d(header)
        assertEquals("6fe28c0ab6f1b372c1a6a246ae63f74f931e8365e15a089c68d6190000000000", Hash.hex(h))
    }

    @Test
    fun pbkdf2MatchesKnownVector() {
        // RFC 6070-style vector for PBKDF2-HMAC-SHA512 (password, salt, 1 iteration, 64 bytes)
        val out = Hash.pbkdf2HmacSha512("password".toByteArray(), "salt".toByteArray(), 1, 64)
        assertEquals(
            "867f70cf1ade02cff3752599a3a53dc4af34c7a669815ae5d513554e1c8cf252c02d470a285a0501bad999bfe943c08f050235d7d68b1da55e63f73b60a57fce",
            Hash.hex(out)
        )
    }
}

/** Vetor de teste 1 do BIP32 (semente 000102...0f). */
class Bip32Test {
    private val seed = Hash.unhex("000102030405060708090a0b0c0d0e0f")

    @Test
    fun master() {
        val m = Bip32.masterFromSeed(seed)
        assertEquals(
            "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8",
            m.neutered().serialize(Bip32.XPUB)
        )
        assertEquals(
            "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi",
            m.serialize(Bip32.XPRV)
        )
    }

    @Test
    fun chain() {
        val m = Bip32.masterFromSeed(seed)
        val expectations = mapOf(
            "m/0'" to "xpub68Gmy5EdvgibQVfPdqkBBCHxA5htiqg55crXYuXoQRKfDBFA1WEjWgP6LHhwBZeNK1VTsfTFUHCdrfp1bgwQ9xv5ski8PX9rL2dZXvgGDnw",
            "m/0'/1" to "xpub6ASuArnXKPbfEwhqN6e3mwBcDTgzisQN1wXN9BJcM47sSikHjJf3UFHKkNAWbWMiGj7Wf5uMash7SyYq527Hqck2AxYysAA7xmALppuCkwQ",
            "m/0'/1/2'" to "xpub6D4BDPcP2GT577Vvch3R8wDkScZWzQzMMUm3PWbmWvVJrZwQY4VUNgqFJPMM3No2dFDFGTsxxpG5uJh7n7epu4trkrX7x7DogT5Uv6fcLW5",
            "m/0'/1/2'/2" to "xpub6FHa3pjLCk84BayeJxFW2SP4XRrFd1JYnxeLeU8EqN3vDfZmbqBqaGJAyiLjTAwm6ZLRQUMv1ZACTj37sR62cfN7fe5JnJ7dh8zL4fiyLHV",
            "m/0'/1/2'/2/1000000000" to "xpub6H1LXWLaKsWFhvm6RVpEL9P4KfRZSW7abD2ttkWP3SSQvnyA8FSVqNTEcYFgJS2UaFcxupHiYkro49S8yGasTvXEYBVPamhGW6cFJodrTHy"
        )
        for ((path, xpub) in expectations) {
            val k = Bip32.derivePath(m, path)
            assertEquals(path, xpub, k.neutered().serialize(Bip32.XPUB))
        }
    }

    @Test
    fun publicDerivationMatchesPrivate() {
        val m = Bip32.masterFromSeed(seed)
        val acct = Bip32.derivePath(m, "m/0'/1")
        val viaPriv = acct.deriveChild(2).neutered()
        val viaPub = acct.neutered().deriveChild(2)
        assertArrayEquals(viaPriv.key, viaPub.key)
        assertArrayEquals(viaPriv.chainCode, viaPub.chainCode)
    }
}

/** Vetores do BIP84 (frase "abandon x11 about"). */
class Bip84WalletTest {
    private val bip39 = TestWordlist.bip39
    private val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" ")

    @Test
    fun firstAddressAndZpub() {
        val w = WalletFactory.fromMnemonic(bip39, words, "")
        assertEquals("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", w.address)
        assertEquals(
            "zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs",
            w.zpub
        )
        assertEquals("73c5da0a", w.masterFingerprint)
        assertEquals("m/84'/0'/0'/0/0", w.derivationPath)
    }

    @Test
    fun otherAddressesOfSameAccount() {
        val seed = bip39.toSeed(words, "")
        val m = Bip32.masterFromSeed(seed)
        val second = Bip32.derivePath(m, "m/84'/0'/0'/0/1")
        assertEquals("03e775fd51f0dfb8cd865d9ff1cca2a158cf651fe997fdc9fee9c1d3b5e995ea77", Hash.hex(second.publicKey()))
        assertEquals("bc1qnjg0jd8228aq7egyzacy8cys3knf9xvrerkf9g", Bech32.encodeSegwit("bc", 0, Hash.hash160(second.publicKey())))
        val change = Bip32.derivePath(m, "m/84'/0'/0'/1/0")
        assertEquals("bc1q8c6fshw2dlwun7ekn9qwf37cu2rn755upcp6el", Bech32.encodeSegwit("bc", 0, Hash.hash160(change.publicKey())))
        val first = Bip32.derivePath(m, "m/84'/0'/0'/0/0")
        assertEquals("0330d54fd0dd420a6e5f8d3624f5f3482cae350f79d5f0753bf5beef9c2d91af3c", Hash.hex(first.publicKey()))
    }

    @Test
    fun passphraseChangesWallet() {
        val a = WalletFactory.fromMnemonic(bip39, words, "")
        val b = WalletFactory.fromMnemonic(bip39, words, "senha")
        assertFalse(a.address == b.address)
        assertTrue(b.address.startsWith("bc1q"))
    }

    @Test
    fun createdWalletIsConsistent() {
        val created = WalletFactory.create(bip39)
        assertEquals(24, created.words.size)
        val again = WalletFactory.fromMnemonic(bip39, created.words)
        assertEquals(created.address, again.address)
        assertEquals(created.zpub, again.zpub)
        assertTrue(AddressValidator.validate(created.address).valid)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidMnemonicThrows() {
        WalletFactory.fromMnemonic(bip39, List(24) { "abandon" })
    }
}

class AddressValidatorTest {
    @Test
    fun validAddresses() {
        assertEquals(AddressValidator.Type.P2WPKH, AddressValidator.validate("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu").type)
        assertEquals(AddressValidator.Type.P2WPKH, AddressValidator.validate(" BC1QCR8TE4KR609GCAWUTMRZA0J4XV80JY8Z306FYU ").type)
        assertEquals(AddressValidator.Type.P2TR, AddressValidator.validate("bc1p5cyxnuxmeuwuvkwfem96lqzszd02n6xdcjrs20cac6yqjjwudpxqkedrcr").type)
        assertEquals(AddressValidator.Type.P2WSH, AddressValidator.validate("bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3").type)
        assertEquals(AddressValidator.Type.P2PKH, AddressValidator.validate("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa").type)
        assertEquals(AddressValidator.Type.P2SH, AddressValidator.validate("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy").type)
    }

    @Test
    fun invalidAddresses() {
        assertFalse(AddressValidator.validate("").valid)
        assertFalse(AddressValidator.validate("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyx").valid)
        assertFalse(AddressValidator.validate("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNb").valid)
        assertFalse(AddressValidator.validate("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx").valid)
        assertFalse(AddressValidator.validate("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5").valid) // ultimo caractere alterado
        // BIP173: v1 com checksum bech32 (nao bech32m) e invalido pelo BIP350
        assertNull(Bech32.decodeSegwit("bc", "bc1pw508d6qejxtdg4y5r3zarvary0c5xw7kw508d6qejxtdg4y5r3zarvary0c5xw7k7grplx"))
        // BIP350: v2 com bech32m e valido (programa de 16 bytes)
        val v2 = Bech32.decodeSegwit("bc", "bc1zw508d6qejxtdg4y5r3zarvaryvaxxpcs")
        assertEquals(2, v2?.version)
        assertEquals("751e76e8199196d454941c45d1b3a323", v2?.program?.let { Hash.hex(it) })
        assertEquals(AddressValidator.Type.FUTURE_SEGWIT, AddressValidator.validate("bc1zw508d6qejxtdg4y5r3zarvaryvaxxpcs").type)
    }
}
