package br.com.cavaleiros.mineradores.wallet

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Primitivas de hash usadas pela carteira.
 * SHA-256 e HMAC-SHA512 vem do provedor JCA do Android (BoringSSL).
 * RIPEMD-160 e PBKDF2-HMAC-SHA512 sao implementados aqui porque nem todo
 * Android expoe esses algoritmos na JCA.
 */
object Hash {

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun sha256(a: ByteArray, b: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(a)
        md.update(b)
        return md.digest()
    }

    /** SHA-256 aplicado duas vezes (padrao Bitcoin). */
    fun sha256d(data: ByteArray): ByteArray = sha256(sha256(data))

    /** HASH160 = RIPEMD160(SHA256(x)). */
    fun hash160(data: ByteArray): ByteArray = Ripemd160.digest(sha256(data))

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    /**
     * PBKDF2-HMAC-SHA512. Para dkLen <= 64 (caso do BIP39) basta um unico bloco.
     * Implementado manualmente para funcionar em qualquer versao do Android.
     *
     * SecretKeySpec nao aceita chave vazia; como o HMAC preenche a chave com zeros
     * ate o tamanho do bloco, uma chave de um unico byte zero e equivalente.
     */
    fun pbkdf2HmacSha512(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        require(iterations > 0)
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(if (password.isEmpty()) ByteArray(1) else password, "HmacSHA512"))
        val hLen = 64
        val blocks = (dkLen + hLen - 1) / hLen
        val out = ByteArray(blocks * hLen)
        for (block in 1..blocks) {
            val blockIndex = byteArrayOf(
                (block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()
            )
            mac.update(salt)
            mac.update(blockIndex)
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (block - 1) * hLen, hLen)
            t.fill(0)
            u.fill(0)
        }
        if (out.size == dkLen) return out
        val trimmed = out.copyOf(dkLen)
        out.fill(0)
        return trimmed
    }

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    fun unhex(hex: String): ByteArray {
        val s = hex.trim()
        require(s.length % 2 == 0) { "hex de tamanho impar" }
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(s[2 * i], 16)
            val lo = Character.digit(s[2 * i + 1], 16)
            require(hi >= 0 && lo >= 0) { "hex invalido" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private val HEX = "0123456789abcdef".toCharArray()
}

/**
 * RIPEMD-160 (ISO/IEC 10118-3). Implementacao direta da especificacao.
 */
object Ripemd160 {
    private val RL = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
        3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12,
        1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2,
        4, 0, 5, 9, 7, 12, 2, 10, 14, 1, 3, 8, 11, 6, 15, 13
    )
    private val RR = intArrayOf(
        5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
        6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
        15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13,
        8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14,
        12, 15, 10, 4, 1, 5, 8, 7, 6, 2, 13, 14, 0, 3, 9, 11
    )
    private val SL = intArrayOf(
        11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8,
        7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
        11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5,
        11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12,
        9, 15, 5, 11, 6, 8, 13, 12, 5, 12, 13, 14, 11, 8, 5, 6
    )
    private val SR = intArrayOf(
        8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6,
        9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
        9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5,
        15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8,
        8, 5, 12, 9, 12, 5, 14, 6, 8, 13, 6, 5, 15, 13, 11, 11
    )
    private val KL = intArrayOf(0x00000000, 0x5A827999, 0x6ED9EBA1, 0x8F1BBCDC.toInt(), 0xA953FD4E.toInt())
    private val KR = intArrayOf(0x50A28BE6, 0x5C4DD124, 0x6D703EF3, 0x7A6D76E9, 0x00000000)

    private fun f(j: Int, x: Int, y: Int, z: Int): Int = when (j / 16) {
        0 -> x xor y xor z
        1 -> (x and y) or (x.inv() and z)
        2 -> (x or y.inv()) xor z
        3 -> (x and z) or (y and z.inv())
        else -> x xor (y or z.inv())
    }

    fun digest(message: ByteArray): ByteArray {
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()

        val bitLen = message.size.toLong() * 8
        val padLen = ((56 - (message.size + 1) % 64) + 64) % 64
        val padded = ByteArray(message.size + 1 + padLen + 8)
        System.arraycopy(message, 0, padded, 0, message.size)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 8 + i] = (bitLen ushr (8 * i)).toByte()

        val x = IntArray(16)
        var off = 0
        while (off < padded.size) {
            for (i in 0 until 16) {
                val p = off + i * 4
                x[i] = (padded[p].toInt() and 0xFF) or
                    ((padded[p + 1].toInt() and 0xFF) shl 8) or
                    ((padded[p + 2].toInt() and 0xFF) shl 16) or
                    ((padded[p + 3].toInt() and 0xFF) shl 24)
            }
            var al = h0; var bl = h1; var cl = h2; var dl = h3; var el = h4
            var ar = h0; var br = h1; var cr = h2; var dr = h3; var er = h4
            for (j in 0 until 80) {
                var t = Integer.rotateLeft(al + f(j, bl, cl, dl) + x[RL[j]] + KL[j / 16], SL[j]) + el
                al = el; el = dl; dl = Integer.rotateLeft(cl, 10); cl = bl; bl = t
                t = Integer.rotateLeft(ar + f(79 - j, br, cr, dr) + x[RR[j]] + KR[j / 16], SR[j]) + er
                ar = er; er = dr; dr = Integer.rotateLeft(cr, 10); cr = br; br = t
            }
            val t = h1 + cl + dr
            h1 = h2 + dl + er
            h2 = h3 + el + ar
            h3 = h4 + al + br
            h4 = h0 + bl + cr
            h0 = t
            off += 64
        }
        val out = ByteArray(20)
        for ((i, h) in intArrayOf(h0, h1, h2, h3, h4).withIndex()) {
            out[i * 4] = h.toByte()
            out[i * 4 + 1] = (h ushr 8).toByte()
            out[i * 4 + 2] = (h ushr 16).toByte()
            out[i * 4 + 3] = (h ushr 24).toByte()
        }
        return out
    }
}
