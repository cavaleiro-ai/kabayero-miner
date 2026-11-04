package br.com.cavaleiros.mineradores.wallet

import java.math.BigInteger

/** Base58 e Base58Check (BIP32 xpub/zpub, enderecos legados). */
object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val INDEXES = IntArray(128) { -1 }.also { for (i in ALPHABET.indices) it[ALPHABET[i].code] = i }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        var zeros = 0
        while (zeros < input.size && input[zeros].toInt() == 0) zeros++
        var num = BigInteger(1, input)
        val sb = StringBuilder()
        val base = BigInteger.valueOf(58)
        while (num.signum() > 0) {
            val qr = num.divideAndRemainder(base)
            sb.append(ALPHABET[qr[1].toInt()])
            num = qr[0]
        }
        repeat(zeros) { sb.append('1') }
        return sb.reverse().toString()
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        var num = BigInteger.ZERO
        val base = BigInteger.valueOf(58)
        for (c in input) {
            val idx = if (c.code < 128) INDEXES[c.code] else -1
            require(idx >= 0) { "caractere base58 invalido" }
            num = num.multiply(base).add(BigInteger.valueOf(idx.toLong()))
        }
        var zeros = 0
        while (zeros < input.length && input[zeros] == '1') zeros++
        val raw = num.toByteArray()
        val body = if (raw.isNotEmpty() && raw[0].toInt() == 0) raw.copyOfRange(1, raw.size) else raw
        return ByteArray(zeros) + body
    }

    fun encodeCheck(payload: ByteArray): String {
        val checksum = Hash.sha256d(payload).copyOfRange(0, 4)
        return encode(payload + checksum)
    }

    /** Retorna o payload (sem checksum) ou null se o checksum for invalido. */
    fun decodeCheck(input: String): ByteArray? {
        val raw = try { decode(input) } catch (e: IllegalArgumentException) { return null }
        if (raw.size < 5) return null
        val payload = raw.copyOfRange(0, raw.size - 4)
        val checksum = raw.copyOfRange(raw.size - 4, raw.size)
        val expected = Hash.sha256d(payload).copyOfRange(0, 4)
        return if (checksum.contentEquals(expected)) payload else null
    }
}

/** Bech32 / Bech32m (BIP173 / BIP350) para enderecos SegWit. */
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GEN = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    private const val BECH32_CONST = 1
    private const val BECH32M_CONST = 0x2bc830a3

    enum class Encoding { BECH32, BECH32M }

    class Decoded(val hrp: String, val data: IntArray, val encoding: Encoding)

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0 until 5) if ((top ushr i) and 1 == 1) chk = chk xor GEN[i]
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray {
        val out = IntArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            out[i] = hrp[i].code ushr 5
            out[i + hrp.length + 1] = hrp[i].code and 31
        }
        out[hrp.length] = 0
        return out
    }

    private fun createChecksum(hrp: String, data: IntArray, enc: Encoding): IntArray {
        val values = hrpExpand(hrp) + data + IntArray(6)
        val const = if (enc == Encoding.BECH32) BECH32_CONST else BECH32M_CONST
        val mod = polymod(values) xor const
        return IntArray(6) { (mod ushr (5 * (5 - it))) and 31 }
    }

    fun encode(hrp: String, data: IntArray, enc: Encoding): String {
        val combined = data + createChecksum(hrp, data, enc)
        val sb = StringBuilder(hrp).append('1')
        for (d in combined) sb.append(CHARSET[d])
        return sb.toString()
    }

    fun decode(str: String): Decoded? {
        if (str.length < 8 || str.length > 90) return null
        var lower = false
        var upper = false
        for (c in str) {
            if (c.code < 33 || c.code > 126) return null
            if (c in 'a'..'z') lower = true
            if (c in 'A'..'Z') upper = true
        }
        if (lower && upper) return null
        val s = str.lowercase()
        val pos = s.lastIndexOf('1')
        if (pos < 1 || pos + 7 > s.length) return null
        val hrp = s.substring(0, pos)
        val data = IntArray(s.length - pos - 1)
        for (i in data.indices) {
            val idx = CHARSET.indexOf(s[pos + 1 + i])
            if (idx < 0) return null
            data[i] = idx
        }
        val check = polymod(hrpExpand(hrp) + data)
        val enc = when (check) {
            BECH32_CONST -> Encoding.BECH32
            BECH32M_CONST -> Encoding.BECH32M
            else -> return null
        }
        return Decoded(hrp, data.copyOfRange(0, data.size - 6), enc)
    }

    fun convertBits(data: IntArray, from: Int, to: Int, pad: Boolean): IntArray? {
        var acc = 0
        var bits = 0
        val out = ArrayList<Int>()
        val maxv = (1 shl to) - 1
        for (value in data) {
            if (value < 0 || value ushr from != 0) return null
            acc = (acc shl from) or value
            bits += from
            while (bits >= to) {
                bits -= to
                out.add((acc ushr bits) and maxv)
            }
        }
        if (pad) {
            if (bits > 0) out.add((acc shl (to - bits)) and maxv)
        } else if (bits >= from || ((acc shl (to - bits)) and maxv) != 0) {
            return null
        }
        return out.toIntArray()
    }

    /** Codifica um endereco SegWit (hrp "bc" para mainnet). */
    fun encodeSegwit(hrp: String, witnessVersion: Int, program: ByteArray): String {
        val prog = IntArray(program.size) { program[it].toInt() and 0xFF }
        val data = intArrayOf(witnessVersion) + convertBits(prog, 8, 5, true)!!
        val enc = if (witnessVersion == 0) Encoding.BECH32 else Encoding.BECH32M
        return encode(hrp, data, enc)
    }

    class SegwitAddress(val hrp: String, val version: Int, val program: ByteArray)

    /** Decodifica e valida um endereco SegWit; null se invalido. */
    fun decodeSegwit(expectedHrp: String, address: String): SegwitAddress? {
        val dec = decode(address) ?: return null
        if (dec.hrp != expectedHrp) return null
        if (dec.data.isEmpty()) return null
        val version = dec.data[0]
        if (version > 16) return null
        val prog = convertBits(dec.data.copyOfRange(1, dec.data.size), 5, 8, false) ?: return null
        if (prog.size < 2 || prog.size > 40) return null
        if (version == 0 && prog.size != 20 && prog.size != 32) return null
        if (version == 0 && dec.encoding != Encoding.BECH32) return null
        if (version != 0 && dec.encoding != Encoding.BECH32M) return null
        return SegwitAddress(dec.hrp, version, ByteArray(prog.size) { prog[it].toByte() })
    }
}
