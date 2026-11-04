package br.com.cavaleiros.mineradores.wallet

import java.math.BigInteger

/**
 * Assinatura ECDSA sobre secp256k1 no padrao do Bitcoin:
 * nonce deterministico RFC 6979 (HMAC-SHA256), S baixo (BIP62) e codificacao DER.
 *
 * Usado apenas no momento do envio: a chave privada vive em memoria durante a
 * assinatura e e zerada pelo chamador logo depois.
 */
object Ecdsa {
    private val N = Secp256k1.N
    private val HALF_N: BigInteger = N.shiftRight(1)

    class Signature(val r: BigInteger, val s: BigInteger) {
        /** DER: 0x30 len 0x02 len(r) r 0x02 len(s) s, inteiros minimos positivos. */
        fun toDer(): ByteArray {
            val rb = r.toByteArray()   // toByteArray ja e minimo com byte de sinal quando preciso
            val sb = s.toByteArray()
            val out = ByteArray(6 + rb.size + sb.size)
            out[0] = 0x30
            out[1] = (4 + rb.size + sb.size).toByte()
            out[2] = 0x02
            out[3] = rb.size.toByte()
            System.arraycopy(rb, 0, out, 4, rb.size)
            out[4 + rb.size] = 0x02
            out[5 + rb.size] = sb.size.toByte()
            System.arraycopy(sb, 0, out, 6 + rb.size, sb.size)
            return out
        }
    }

    /** Assina o hash de 32 bytes com a chave privada (32 bytes big-endian). */
    fun sign(privateKey: ByteArray, hash32: ByteArray): Signature {
        require(privateKey.size == 32 && hash32.size == 32)
        val d = BigInteger(1, privateKey)
        require(d.signum() > 0 && d < N) { "chave privada invalida" }
        val z = BigInteger(1, hash32)
        var attempt = 0
        while (true) {
            val k = rfc6979Nonce(privateKey, hash32, attempt++)
            val rPoint = Secp256k1.G.multiply(k)
            val r = rPoint.x!!.mod(N)
            if (r.signum() == 0) continue
            var s = k.modInverse(N).multiply(z.add(r.multiply(d))).mod(N)
            if (s.signum() == 0) continue
            if (s > HALF_N) s = N.subtract(s)   // S baixo
            return Signature(r, s)
        }
    }

    /** Verifica (r, s) contra a chave publica comprimida; usado como checagem antes de transmitir. */
    fun verify(publicKey33: ByteArray, hash32: ByteArray, sig: Signature): Boolean {
        if (sig.r.signum() <= 0 || sig.r >= N || sig.s.signum() <= 0 || sig.s >= N) return false
        val q = Secp256k1.decodePoint(publicKey33)
        val z = BigInteger(1, hash32)
        val w = sig.s.modInverse(N)
        val u1 = z.multiply(w).mod(N)
        val u2 = sig.r.multiply(w).mod(N)
        val p1 = if (u1.signum() == 0) Secp256k1.Point.INFINITY else Secp256k1.G.multiply(u1)
        val p2 = if (u2.signum() == 0) Secp256k1.Point.INFINITY else q.multiply(u2)
        val sum = p1.add(p2)
        if (sum.isInfinity) return false
        return sum.x!!.mod(N) == sig.r
    }

    fun parseDer(der: ByteArray): Signature? {
        if (der.size < 8 || der[0].toInt() != 0x30) return null
        var i = 2
        if (der[i].toInt() != 0x02) return null
        val rLen = der[i + 1].toInt() and 0xFF
        val r = BigInteger(1, der.copyOfRange(i + 2, i + 2 + rLen))
        i += 2 + rLen
        if (i >= der.size || der[i].toInt() != 0x02) return null
        val sLen = der[i + 1].toInt() and 0xFF
        if (i + 2 + sLen > der.size) return null
        val s = BigInteger(1, der.copyOfRange(i + 2, i + 2 + sLen))
        return Signature(r, s)
    }

    /**
     * RFC 6979, secao 3.2, com HMAC-SHA256 e qlen = 256. [extra] conta quantos candidatos
     * ja foram rejeitados (r == 0 ou s == 0, praticamente impossivel), seguindo o passo h.3.
     */
    internal fun rfc6979Nonce(privateKey: ByteArray, hash32: ByteArray, extra: Int): BigInteger {
        val x = privateKey
        val h1 = to32(BigInteger(1, hash32).mod(N))   // bits2octets
        var v = ByteArray(32) { 0x01 }
        var k = ByteArray(32) { 0x00 }
        k = Hash.hmacSha256(k, v + byteArrayOf(0x00) + x + h1)
        v = Hash.hmacSha256(k, v)
        k = Hash.hmacSha256(k, v + byteArrayOf(0x01) + x + h1)
        v = Hash.hmacSha256(k, v)
        var skipped = 0
        while (true) {
            v = Hash.hmacSha256(k, v)
            val candidate = BigInteger(1, v)
            if (candidate.signum() > 0 && candidate < N) {
                if (skipped == extra) return candidate
                skipped++
            }
            k = Hash.hmacSha256(k, v + byteArrayOf(0x00))
            v = Hash.hmacSha256(k, v)
        }
    }

    private fun to32(v: BigInteger): ByteArray {
        val raw = v.toByteArray()
        val out = ByteArray(32)
        val start = if (raw.size > 32) raw.size - 32 else 0
        val len = raw.size - start
        System.arraycopy(raw, start, out, 32 - len, len)
        return out
    }
}
