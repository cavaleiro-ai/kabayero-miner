package br.com.cavaleiros.mineradores.mining

/**
 * SHA-256d especializado para cabecalhos de bloco Bitcoin (80 bytes).
 *
 * O cabecalho ocupa dois blocos de 64 bytes. Os primeiros 64 bytes nao mudam
 * durante a busca do nonce, entao o estado apos o primeiro bloco ("midstate")
 * e calculado uma unica vez por job. Para cada nonce restam duas compressoes:
 * a do segundo bloco e a do segundo SHA-256 sobre os 32 bytes resultantes.
 *
 * Nenhuma alocacao acontece dentro de [hash]; os buffers sao reutilizados.
 * Cada thread de mineracao deve ter sua propria instancia.
 */
class Sha256Miner {
    private val w = IntArray(64)
    private val s = IntArray(8)
    private val s2 = IntArray(8)

    /**
     * Calcula sha256d(header) para o nonce indicado.
     *
     * @param midstate estado apos o primeiro bloco (ver [midstate]).
     * @param tail0 palavra 16 do cabecalho (ultimos 4 bytes da merkle root).
     * @param tail1 palavra 17 (ntime, como palavra big-endian dos bytes do cabecalho).
     * @param tail2 palavra 18 (nbits).
     * @param nonceWord palavra 19 (nonce, como palavra big-endian dos bytes do cabecalho).
     * @return a ultima palavra do hash (s2[7]). Como o hash e interpretado como
     *         inteiro little-endian, os 16 bits baixos desta palavra sao os bits
     *         mais significativos do valor: se nao forem zero, o hash certamente
     *         nao atinge nenhum alvo com dificuldade >= 2^-16.
     */
    fun hash(midstate: IntArray, tail0: Int, tail1: Int, tail2: Int, nonceWord: Int): Int {
        val w = this.w
        w[0] = tail0; w[1] = tail1; w[2] = tail2; w[3] = nonceWord
        w[4] = 0x80000000.toInt()
        w[5] = 0; w[6] = 0; w[7] = 0; w[8] = 0; w[9] = 0; w[10] = 0; w[11] = 0; w[12] = 0; w[13] = 0; w[14] = 0
        w[15] = 640 // 80 bytes * 8 bits

        val s = this.s
        s[0] = midstate[0]; s[1] = midstate[1]; s[2] = midstate[2]; s[3] = midstate[3]
        s[4] = midstate[4]; s[5] = midstate[5]; s[6] = midstate[6]; s[7] = midstate[7]
        compress(s, w)

        // segundo SHA-256: mensagem = 32 bytes do primeiro hash
        w[0] = s[0]; w[1] = s[1]; w[2] = s[2]; w[3] = s[3]
        w[4] = s[4]; w[5] = s[5]; w[6] = s[6]; w[7] = s[7]
        w[8] = 0x80000000.toInt()
        w[9] = 0; w[10] = 0; w[11] = 0; w[12] = 0; w[13] = 0; w[14] = 0
        w[15] = 256

        val s2 = this.s2
        s2[0] = H0_0; s2[1] = H0_1; s2[2] = H0_2; s2[3] = H0_3
        s2[4] = H0_4; s2[5] = H0_5; s2[6] = H0_6; s2[7] = H0_7
        compress(s2, w)
        return s2[7]
    }

    /** Hash completo (32 bytes, ordem interna do Bitcoin) do ultimo [hash] calculado. */
    fun lastHash(): ByteArray = wordsToBytes(s2)

    companion object {
        private const val H0_0 = 0x6a09e667
        private const val H0_1 = 0xbb67ae85.toInt()
        private const val H0_2 = 0x3c6ef372
        private const val H0_3 = 0xa54ff53a.toInt()
        private const val H0_4 = 0x510e527f
        private const val H0_5 = 0x9b05688c.toInt()
        private const val H0_6 = 0x1f83d9ab
        private const val H0_7 = 0x5be0cd19

        private val K = intArrayOf(
            0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
            0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
            0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
            0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
        )

        /** Funcao de compressao SHA-256. [w] deve conter as 16 palavras da mensagem em w[0..15]. */
        fun compress(state: IntArray, w: IntArray) {
            for (i in 16 until 64) {
                val w15 = w[i - 15]
                val w2 = w[i - 2]
                val s0 = Integer.rotateRight(w15, 7) xor Integer.rotateRight(w15, 18) xor (w15 ushr 3)
                val s1 = Integer.rotateRight(w2, 17) xor Integer.rotateRight(w2, 19) xor (w2 ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
            var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
            val k = K
            for (i in 0 until 64) {
                val bigS1 = Integer.rotateRight(e, 6) xor Integer.rotateRight(e, 11) xor Integer.rotateRight(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = h + bigS1 + ch + k[i] + w[i]
                val bigS0 = Integer.rotateRight(a, 2) xor Integer.rotateRight(a, 13) xor Integer.rotateRight(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = bigS0 + maj
                h = g; g = f; f = e; e = d + t1
                d = c; c = b; b = a; a = t1 + t2
            }
            state[0] += a; state[1] += b; state[2] += c; state[3] += d
            state[4] += e; state[5] += f; state[6] += g; state[7] += h
        }

        /** Converte os 80 bytes do cabecalho em 20 palavras big-endian. */
        fun headerWords(header: ByteArray): IntArray {
            require(header.size == 80) { "cabecalho deve ter 80 bytes" }
            val words = IntArray(20)
            for (i in 0 until 20) {
                val p = i * 4
                words[i] = ((header[p].toInt() and 0xFF) shl 24) or
                    ((header[p + 1].toInt() and 0xFF) shl 16) or
                    ((header[p + 2].toInt() and 0xFF) shl 8) or
                    (header[p + 3].toInt() and 0xFF)
            }
            return words
        }

        /** Estado SHA-256 apos processar os primeiros 64 bytes (palavras 0..15) do cabecalho. */
        fun midstate(headerWords: IntArray): IntArray {
            val state = intArrayOf(H0_0, H0_1, H0_2, H0_3, H0_4, H0_5, H0_6, H0_7)
            val w = IntArray(64)
            System.arraycopy(headerWords, 0, w, 0, 16)
            compress(state, w)
            return state
        }

        fun wordsToBytes(words: IntArray): ByteArray {
            val out = ByteArray(words.size * 4)
            for (i in words.indices) {
                val v = words[i]
                out[i * 4] = (v ushr 24).toByte()
                out[i * 4 + 1] = (v ushr 16).toByte()
                out[i * 4 + 2] = (v ushr 8).toByte()
                out[i * 4 + 3] = v.toByte()
            }
            return out
        }

        /**
         * Nonce em hexadecimal para mining.submit. O protocolo espera o valor
         * numerico do nonce em big-endian; a palavra do cabecalho e o valor com
         * bytes invertidos (o campo e little-endian no cabecalho).
         */
        fun nonceHex(nonceWord: Int): String = String.format("%08x", Integer.reverseBytes(nonceWord))
    }
}
