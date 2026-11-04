package br.com.cavaleiros.mineradores.wallet

import java.math.BigInteger

/**
 * Aritmetica da curva secp256k1 em coordenadas afins com BigInteger.
 * Usada apenas para derivacao de chaves publicas (poucas operacoes), por isso
 * a simplicidade e preferida sobre a velocidade.
 */
object Secp256k1 {
    val P: BigInteger = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
    val N: BigInteger = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    private val GX = BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16)
    private val GY = BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)
    val G = Point(GX, GY)

    private val TWO: BigInteger = BigInteger.valueOf(2)
    private val THREE: BigInteger = BigInteger.valueOf(3)
    private val SEVEN: BigInteger = BigInteger.valueOf(7)

    /** Ponto da curva; (null, null) representa o ponto no infinito. */
    class Point(val x: BigInteger?, val y: BigInteger?) {
        val isInfinity: Boolean get() = x == null

        fun add(other: Point): Point {
            if (isInfinity) return other
            if (other.isInfinity) return this
            val x1 = x!!; val y1 = y!!; val x2 = other.x!!; val y2 = other.y!!
            if (x1 == x2) {
                return if (y1 == y2) double() else INFINITY
            }
            val lambda = (y2 - y1).mod(P).multiply((x2 - x1).mod(P).modInverse(P)).mod(P)
            val x3 = (lambda * lambda - x1 - x2).mod(P)
            val y3 = (lambda * (x1 - x3) - y1).mod(P)
            return Point(x3, y3)
        }

        fun double(): Point {
            if (isInfinity) return this
            val x1 = x!!; val y1 = y!!
            if (y1.signum() == 0) return INFINITY
            val lambda = (THREE * x1 * x1).mod(P)
                .multiply((TWO * y1).mod(P).modInverse(P)).mod(P)
            val x3 = (lambda * lambda - TWO * x1).mod(P)
            val y3 = (lambda * (x1 - x3) - y1).mod(P)
            return Point(x3, y3)
        }

        fun multiply(k: BigInteger): Point {
            require(k.signum() > 0 && k < N) { "escalar fora do intervalo" }
            var result = INFINITY
            var addend: Point = this
            var n = k
            while (n.signum() > 0) {
                if (n.testBit(0)) result = result.add(addend)
                addend = addend.double()
                n = n.shiftRight(1)
            }
            return result
        }

        /** Serializacao SEC1 comprimida (33 bytes). */
        fun encodeCompressed(): ByteArray {
            require(!isInfinity)
            val out = ByteArray(33)
            out[0] = if (y!!.testBit(0)) 0x03 else 0x02
            val xb = x!!.toByteArray()
            // toByteArray pode ter byte de sinal extra ou menos de 32 bytes
            val start = if (xb.size > 32) xb.size - 32 else 0
            val len = xb.size - start
            System.arraycopy(xb, start, out, 1 + (32 - len), len)
            return out
        }

        companion object {
            val INFINITY = Point(null, null)
        }
    }

    /** Chave publica comprimida a partir da chave privada (32 bytes big-endian). */
    fun publicKey(privateKey: ByteArray): ByteArray {
        val k = BigInteger(1, privateKey)
        return G.multiply(k).encodeCompressed()
    }

    /** Decodifica uma chave publica comprimida de volta para um ponto. */
    fun decodePoint(compressed: ByteArray): Point {
        require(compressed.size == 33 && (compressed[0].toInt() == 2 || compressed[0].toInt() == 3))
        val x = BigInteger(1, compressed.copyOfRange(1, 33))
        // y^2 = x^3 + 7 ; como p % 4 == 3, y = (y^2)^((p+1)/4) mod p
        val rhs = (x.modPow(THREE, P) + SEVEN).mod(P)
        var y = rhs.modPow(P.add(BigInteger.ONE).shiftRight(2), P)
        val odd = compressed[0].toInt() == 3
        if (y.testBit(0) != odd) y = P.subtract(y)
        return Point(x, y)
    }
}
