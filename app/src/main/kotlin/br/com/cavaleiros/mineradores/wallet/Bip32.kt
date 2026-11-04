package br.com.cavaleiros.mineradores.wallet

import java.math.BigInteger

/**
 * Chave estendida BIP32. Para chaves privadas, [key] tem 32 bytes; para
 * publicas, 33 bytes (SEC1 comprimida). Chame [wipe] quando terminar.
 */
class ExtendedKey(
    val key: ByteArray,
    val chainCode: ByteArray,
    val depth: Int,
    val parentFingerprint: Int,
    val childNumber: Int,
    val isPrivate: Boolean
) {
    init {
        require(chainCode.size == 32)
        require(if (isPrivate) key.size == 32 else key.size == 33)
    }

    fun publicKey(): ByteArray = if (isPrivate) Secp256k1.publicKey(key) else key.copyOf()

    fun fingerprint(): Int {
        val h = Hash.hash160(publicKey())
        return ((h[0].toInt() and 0xFF) shl 24) or ((h[1].toInt() and 0xFF) shl 16) or
            ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
    }

    /** CKDpriv / CKDpub. Indices >= 0x80000000 sao hardened. */
    fun deriveChild(index: Int): ExtendedKey {
        val hardened = (index and Bip32.HARDENED) != 0
        val data = ByteArray(37)
        if (hardened) {
            require(isPrivate) { "derivacao hardened exige chave privada" }
            data[0] = 0
            System.arraycopy(key, 0, data, 1, 32)
        } else {
            val pub = publicKey()
            System.arraycopy(pub, 0, data, 0, 33)
        }
        data[33] = (index ushr 24).toByte()
        data[34] = (index ushr 16).toByte()
        data[35] = (index ushr 8).toByte()
        data[36] = index.toByte()

        val i = Hash.hmacSha512(chainCode, data)
        data.fill(0)
        val il = i.copyOfRange(0, 32)
        val ir = i.copyOfRange(32, 64)
        i.fill(0)

        val ilInt = BigInteger(1, il)
        require(ilInt < Secp256k1.N) { "IL >= n (chave invalida, tente outro indice)" }

        val childKey: ByteArray
        if (isPrivate) {
            val k = (ilInt + BigInteger(1, key)).mod(Secp256k1.N)
            require(k.signum() != 0) { "chave filha nula" }
            childKey = to32Bytes(k)
        } else {
            val point = Secp256k1.G.multiply(ilInt).add(Secp256k1.decodePoint(key))
            require(!point.isInfinity) { "ponto no infinito" }
            childKey = point.encodeCompressed()
        }
        il.fill(0)
        return ExtendedKey(childKey, ir, depth + 1, fingerprint(), index, isPrivate)
    }

    /** Versao somente-publica (xpub/zpub). */
    fun neutered(): ExtendedKey =
        if (!isPrivate) this
        else ExtendedKey(publicKey(), chainCode.copyOf(), depth, parentFingerprint, childNumber, false)

    /** Serializacao Base58Check (xprv/xpub/zprv/zpub conforme [version]). */
    fun serialize(version: Int): String {
        val out = ByteArray(78)
        out[0] = (version ushr 24).toByte(); out[1] = (version ushr 16).toByte()
        out[2] = (version ushr 8).toByte(); out[3] = version.toByte()
        out[4] = depth.toByte()
        out[5] = (parentFingerprint ushr 24).toByte(); out[6] = (parentFingerprint ushr 16).toByte()
        out[7] = (parentFingerprint ushr 8).toByte(); out[8] = parentFingerprint.toByte()
        out[9] = (childNumber ushr 24).toByte(); out[10] = (childNumber ushr 16).toByte()
        out[11] = (childNumber ushr 8).toByte(); out[12] = childNumber.toByte()
        System.arraycopy(chainCode, 0, out, 13, 32)
        if (isPrivate) {
            out[45] = 0
            System.arraycopy(key, 0, out, 46, 32)
        } else {
            System.arraycopy(key, 0, out, 45, 33)
        }
        val s = Base58.encodeCheck(out)
        out.fill(0)
        return s
    }

    fun wipe() {
        key.fill(0)
        chainCode.fill(0)
    }

    private fun to32Bytes(v: BigInteger): ByteArray {
        val raw = v.toByteArray()
        val out = ByteArray(32)
        val start = if (raw.size > 32) raw.size - 32 else 0
        val len = raw.size - start
        System.arraycopy(raw, start, out, 32 - len, len)
        raw.fill(0)
        return out
    }
}

object Bip32 {
    const val HARDENED: Int = 0x80000000.toInt()

    // Prefixos de versao (mainnet)
    const val XPRV = 0x0488ADE4
    const val XPUB = 0x0488B21E
    const val ZPRV = 0x04B2430C          // BIP84 (P2WPKH)
    const val ZPUB = 0x04B24746

    private val MASTER_KEY = "Bitcoin seed".toByteArray(Charsets.US_ASCII)

    fun masterFromSeed(seed: ByteArray): ExtendedKey {
        require(seed.size in 16..64) { "semente deve ter 16..64 bytes" }
        val i = Hash.hmacSha512(MASTER_KEY, seed)
        val il = i.copyOfRange(0, 32)
        val ir = i.copyOfRange(32, 64)
        i.fill(0)
        val k = BigInteger(1, il)
        require(k.signum() != 0 && k < Secp256k1.N) { "semente gera chave mestra invalida" }
        return ExtendedKey(il, ir, 0, 0, 0, true)
    }

    /** Deriva um caminho textual como "m/84'/0'/0'/0/0" (aceita ' ou h para hardened). */
    fun derivePath(master: ExtendedKey, path: String): ExtendedKey {
        val parts = path.trim().split('/')
        require(parts.isNotEmpty() && (parts[0] == "m" || parts[0] == "M")) { "caminho deve iniciar com m/" }
        var current = master
        for (p in parts.drop(1)) {
            if (p.isEmpty()) continue
            val hardened = p.endsWith("'") || p.endsWith("h") || p.endsWith("H")
            val num = p.trimEnd('\'', 'h', 'H').toLong()
            require(num in 0 until 0x80000000L) { "indice fora do intervalo" }
            val index = (num.toInt()) or (if (hardened) HARDENED else 0)
            val next = current.deriveChild(index)
            if (current !== master) current.wipe()
            current = next
        }
        return current
    }
}
