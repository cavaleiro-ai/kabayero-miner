package br.com.cavaleiros.mineradores.wallet

import java.io.InputStream
import java.security.SecureRandom
import java.text.Normalizer

/**
 * BIP39: geracao de frase mnemonica (24 palavras = 256 bits de entropia),
 * validacao do checksum e derivacao da semente via PBKDF2-HMAC-SHA512.
 *
 * Esta classe NUNCA persiste nada: a frase so existe em memoria enquanto a
 * tela de criacao estiver aberta.
 */
class Bip39(val wordlist: List<String>) {

    private val index: Map<String, Int> = HashMap<String, Int>(4096).apply {
        wordlist.forEachIndexed { i, w -> put(w, i) }
    }

    init {
        require(wordlist.size == 2048) { "wordlist BIP39 deve ter 2048 palavras" }
        require(index.size == 2048) { "wordlist BIP39 contem palavras duplicadas" }
    }

    /** Entropia criptograficamente segura (padrao 256 bits -> 24 palavras). */
    fun generateEntropy(bits: Int = 256): ByteArray {
        require(bits in VALID_BITS) { "entropia deve ter 128..256 bits em multiplos de 32" }
        val entropy = ByteArray(bits / 8)
        SecureRandom().nextBytes(entropy)
        return entropy
    }

    fun entropyToMnemonic(entropy: ByteArray): List<String> {
        require(entropy.size * 8 in VALID_BITS) { "tamanho de entropia invalido" }
        val ent = entropy.size * 8
        val cs = ent / 32
        val hash = Hash.sha256(entropy)
        val totalBits = ent + cs
        val words = ArrayList<String>(totalBits / 11)
        var i = 0
        while (i < totalBits) {
            var idx = 0
            for (b in 0 until 11) {
                val bit = i + b
                val source = if (bit < ent) entropy else hash
                val pos = if (bit < ent) bit else bit - ent
                val bitValue = (source[pos / 8].toInt() shr (7 - pos % 8)) and 1
                idx = (idx shl 1) or bitValue
            }
            words.add(wordlist[idx])
            i += 11
        }
        return words
    }

    /** Converte palavras em entropia validando o checksum; null se invalido. */
    fun mnemonicToEntropy(words: List<String>): ByteArray? {
        if (words.size !in VALID_WORD_COUNTS) return null
        val totalBits = words.size * 11
        val cs = totalBits / 33
        val ent = totalBits - cs
        val bits = BooleanArray(totalBits)
        for ((w, word) in words.withIndex()) {
            val idx = index[normalizeWord(word)] ?: return null
            for (b in 0 until 11) bits[w * 11 + b] = ((idx shr (10 - b)) and 1) == 1
        }
        val entropy = ByteArray(ent / 8)
        for (i in 0 until ent) if (bits[i]) entropy[i / 8] = (entropy[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
        val hash = Hash.sha256(entropy)
        for (i in 0 until cs) {
            val expected = ((hash[i / 8].toInt() shr (7 - i % 8)) and 1) == 1
            if (bits[ent + i] != expected) {
                entropy.fill(0)
                return null
            }
        }
        return entropy
    }

    fun isValid(words: List<String>): Boolean {
        val e = mnemonicToEntropy(words) ?: return false
        e.fill(0)
        return true
    }

    fun isWord(word: String): Boolean = index.containsKey(normalizeWord(word))

    fun suggestions(prefix: String, limit: Int = 6): List<String> {
        val p = normalizeWord(prefix)
        if (p.isEmpty()) return emptyList()
        return wordlist.asSequence().filter { it.startsWith(p) }.take(limit).toList()
    }

    /**
     * Semente BIP39 (64 bytes). O chamador DEVE zerar o array apos o uso.
     * A frase e a passphrase sao normalizadas em NFKD conforme a especificacao.
     */
    fun toSeed(words: List<String>, passphrase: String = ""): ByteArray {
        val mnemonic = Normalizer.normalize(words.joinToString(" ") { normalizeWord(it) }, Normalizer.Form.NFKD)
        val salt = Normalizer.normalize("mnemonic$passphrase", Normalizer.Form.NFKD)
        val pw = mnemonic.toByteArray(Charsets.UTF_8)
        val saltBytes = salt.toByteArray(Charsets.UTF_8)
        try {
            return Hash.pbkdf2HmacSha512(pw, saltBytes, 2048, 64)
        } finally {
            pw.fill(0)
            saltBytes.fill(0)
        }
    }

    companion object {
        val VALID_BITS = setOf(128, 160, 192, 224, 256)
        val VALID_WORD_COUNTS = setOf(12, 15, 18, 21, 24)

        fun normalizeWord(w: String): String =
            Normalizer.normalize(w.trim().lowercase(), Normalizer.Form.NFKD)

        /** Divide texto livre em palavras (aceita quebras de linha, virgulas e espacos multiplos). */
        fun splitWords(text: String): List<String> =
            text.split(Regex("[\\s,;]+")).map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        fun load(stream: InputStream): Bip39 {
            val words = stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
            return Bip39(words)
        }
    }
}
