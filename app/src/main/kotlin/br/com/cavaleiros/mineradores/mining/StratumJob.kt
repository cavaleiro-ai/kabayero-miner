package br.com.cavaleiros.mineradores.mining

import br.com.cavaleiros.mineradores.wallet.Hash
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/** Utilitarios de alvo/dificuldade. */
object Target {
    /** Alvo de dificuldade 1 (bits 0x1d00ffff). */
    val DIFF1: BigInteger = BigInteger("00000000FFFF0000000000000000000000000000000000000000000000000000", 16)

    /**
     * Alvo = DIFF1 / dificuldade. Usa 100 digitos de precisao: o alvo tem ate 78
     * digitos decimais, entao MathContext.DECIMAL128 (34 digitos) truncaria o valor.
     */
    fun fromDifficulty(difficulty: Double): BigInteger {
        require(difficulty > 0 && difficulty.isFinite()) { "dificuldade invalida" }
        return BigDecimal(DIFF1).divide(BigDecimal(difficulty), MathContext(100, RoundingMode.DOWN)).toBigInteger()
    }

    /** Alvo a partir do campo compacto nBits. */
    fun fromBits(bits: Int): BigInteger {
        val exponent = bits ushr 24
        val mantissa = (bits and 0x007fffff).toLong()
        return if (exponent <= 3) {
            BigInteger.valueOf(mantissa shr (8 * (3 - exponent)))
        } else {
            BigInteger.valueOf(mantissa).shiftLeft(8 * (exponent - 3))
        }
    }

    /** Valor numerico de um hash de 32 bytes em ordem interna (little-endian). */
    fun hashToValue(hash: ByteArray): BigInteger {
        require(hash.size == 32)
        val be = ByteArray(32)
        for (i in 0 until 32) be[i] = hash[31 - i]
        return BigInteger(1, be)
    }

    fun meets(hash: ByteArray, target: BigInteger): Boolean = hashToValue(hash) <= target

    /** Dificuldade "atingida" por um hash (DIFF1 / valor). */
    fun difficultyOf(hash: ByteArray): Double {
        val v = hashToValue(hash)
        if (v.signum() == 0) return Double.MAX_VALUE
        return BigDecimal(DIFF1).divide(BigDecimal(v), MathContext.DECIMAL64).toDouble()
    }

    fun difficultyFromBits(bits: Int): Double {
        val t = fromBits(bits)
        if (t.signum() == 0) return Double.MAX_VALUE
        return BigDecimal(DIFF1).divide(BigDecimal(t), MathContext.DECIMAL64).toDouble()
    }

    /** Hash em hexadecimal na forma exibida por exploradores (big-endian). */
    fun displayHex(hash: ByteArray): String {
        val be = ByteArray(hash.size)
        for (i in hash.indices) be[i] = hash[hash.size - 1 - i]
        return Hash.hex(be)
    }
}

/**
 * Job recebido via mining.notify, acompanhado dos parametros de extranonce da
 * sessao. Monta coinbase, merkle root e cabecalho de bloco.
 *
 * Convencoes de bytes do Stratum V1:
 *  - version, nbits, ntime: hex big-endian do valor; no cabecalho sao little-endian.
 *  - prevhash: hex com cada palavra de 4 bytes invertida em relacao a ordem do cabecalho.
 *  - merkle root calculada (saida do sha256d) entra no cabecalho sem alteracao.
 */
class StratumJob(
    val jobId: String,
    val prevHashHex: String,
    val coinb1: ByteArray,
    val coinb2: ByteArray,
    val merkleBranches: List<ByteArray>,
    val versionHex: String,
    val nbitsHex: String,
    val ntimeHex: String,
    val cleanJobs: Boolean,
    val extranonce1: ByteArray,
    val extranonce2Size: Int,
    val receivedAt: Long = System.currentTimeMillis()
) {
    val version: Int = parseHexInt(versionHex)
    val nbits: Int = parseHexInt(nbitsHex)
    val ntime: Int = parseHexInt(ntimeHex)

    val networkTarget: BigInteger by lazy { Target.fromBits(nbits) }
    val networkDifficulty: Double by lazy { Target.difficultyFromBits(nbits) }

    /** Altura do bloco extraida do scriptSig da coinbase (BIP34); null se nao for possivel. */
    val height: Int? by lazy { parseHeight(coinb1) }

    /** Hash do bloco anterior como exibido por exploradores. */
    val prevHashDisplay: String by lazy {
        val internal = prevHashInternalOrder()
        Target.displayHex(internal)
    }

    fun coinbase(extranonce2: ByteArray): ByteArray {
        require(extranonce2.size == extranonce2Size) { "extranonce2 deve ter $extranonce2Size bytes" }
        val out = ByteArray(coinb1.size + extranonce1.size + extranonce2.size + coinb2.size)
        var p = 0
        System.arraycopy(coinb1, 0, out, p, coinb1.size); p += coinb1.size
        System.arraycopy(extranonce1, 0, out, p, extranonce1.size); p += extranonce1.size
        System.arraycopy(extranonce2, 0, out, p, extranonce2.size); p += extranonce2.size
        System.arraycopy(coinb2, 0, out, p, coinb2.size)
        return out
    }

    fun merkleRoot(extranonce2: ByteArray): ByteArray {
        var root = Hash.sha256d(coinbase(extranonce2))
        for (branch in merkleBranches) root = Hash.sha256d(root + branch)
        return root
    }

    /** Bytes do prevhash na ordem interna do cabecalho (cada palavra de 4 bytes invertida). */
    private fun prevHashInternalOrder(): ByteArray {
        val raw = Hash.unhex(prevHashHex)
        require(raw.size == 32) { "prevhash deve ter 32 bytes" }
        val out = ByteArray(32)
        for (i in 0 until 8) {
            out[i * 4] = raw[i * 4 + 3]
            out[i * 4 + 1] = raw[i * 4 + 2]
            out[i * 4 + 2] = raw[i * 4 + 1]
            out[i * 4 + 3] = raw[i * 4]
        }
        return out
    }

    /** Cabecalho de 80 bytes com nonce zero. */
    fun buildHeader(extranonce2: ByteArray, ntimeValue: Int = ntime): ByteArray {
        val h = ByteArray(80)
        putLe32(h, 0, version)
        System.arraycopy(prevHashInternalOrder(), 0, h, 4, 32)
        System.arraycopy(merkleRoot(extranonce2), 0, h, 36, 32)
        putLe32(h, 68, ntimeValue)
        putLe32(h, 72, nbits)
        return h
    }

    /** Codifica um contador como extranonce2 de [extranonce2Size] bytes (big-endian). */
    fun extranonce2For(counter: Long): ByteArray {
        val out = ByteArray(extranonce2Size)
        var v = counter
        for (i in extranonce2Size - 1 downTo 0) {
            out[i] = v.toByte()
            v = v ushr 8
        }
        return out
    }

    companion object {
        fun parseHexInt(hex: String): Int = java.lang.Long.parseLong(hex.trim(), 16).toInt()

        fun putLe32(buf: ByteArray, off: Int, v: Int) {
            buf[off] = v.toByte()
            buf[off + 1] = (v ushr 8).toByte()
            buf[off + 2] = (v ushr 16).toByte()
            buf[off + 3] = (v ushr 24).toByte()
        }

        /**
         * coinb1 = version(4) + vin count(1) + prev txid(32) + prev index(4) + script len(varint) + inicio do scriptSig.
         * Pelo BIP34 o scriptSig comeca com um push da altura em little-endian.
         */
        fun parseHeight(coinb1: ByteArray): Int? {
            try {
                var p = 4 + 1 + 32 + 4
                if (coinb1.size <= p) return null
                val lenByte = coinb1[p].toInt() and 0xFF
                p += when {
                    lenByte < 0xfd -> 1
                    lenByte == 0xfd -> 3
                    lenByte == 0xfe -> 5
                    else -> 9
                }
                if (coinb1.size <= p) return null
                val pushLen = coinb1[p].toInt() and 0xFF
                if (pushLen < 1 || pushLen > 4) return null
                p += 1
                if (coinb1.size < p + pushLen) return null
                var height = 0
                for (i in 0 until pushLen) height = height or ((coinb1[p + i].toInt() and 0xFF) shl (8 * i))
                return if (height > 0) height else null
            } catch (e: Exception) {
                return null
            }
        }
    }
}
