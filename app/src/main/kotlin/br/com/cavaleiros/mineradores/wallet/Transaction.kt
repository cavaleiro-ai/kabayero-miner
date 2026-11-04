package br.com.cavaleiros.mineradores.wallet

import java.io.ByteArrayOutputStream

/** Saida nao gasta de um endereco, como devolvida pelo explorador. */
data class Utxo(val txid: String, val vout: Int, val valueSat: Long, val confirmed: Boolean)

/** Entrada P2WPKH que sera assinada com a chave da carteira. */
class TxInput(val txid: String, val vout: Int, val valueSat: Long, val sequence: Long = SEQUENCE_RBF) {
    companion object {
        /** Sinaliza RBF (BIP125) e permite locktime. */
        const val SEQUENCE_RBF = 0xFFFFFFFDL
    }
}

/** Saida de transacao: valor em satoshis + scriptPubKey. */
class TxOutput(val valueSat: Long, val script: ByteArray)

/** Serializacao binaria no formato do Bitcoin. */
object TxSerial {
    fun u32le(v: Long): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
    )

    fun u64le(v: Long): ByteArray {
        val out = ByteArray(8)
        for (i in 0 until 8) out[i] = ((v shr (8 * i)) and 0xFF).toByte()
        return out
    }

    fun varInt(v: Long): ByteArray = when {
        v < 0xFD -> byteArrayOf(v.toByte())
        v <= 0xFFFF -> byteArrayOf(0xFD.toByte(), (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
        v <= 0xFFFFFFFFL -> byteArrayOf(0xFE.toByte()) + u32le(v)
        else -> byteArrayOf(0xFF.toByte()) + u64le(v)
    }

    fun varIntSize(v: Long): Int = varInt(v).size

    /** txid exibido (big-endian) -> bytes na ordem interna (little-endian). */
    fun txidToInternal(txid: String): ByteArray = Hash.unhex(txid).reversedArray()

    fun serializeOutput(o: TxOutput): ByteArray = u64le(o.valueSat) + varInt(o.script.size.toLong()) + o.script
}

/** scriptPubKey de saida a partir de um endereco mainnet. */
object Script {
    const val DUST_SAT = 546L

    fun fromAddress(address: String): ByteArray {
        val v = AddressValidator.validate(address)
        require(v.valid && v.type != null) { v.message }
        val addr = v.normalized
        if (addr.lowercase().startsWith("bc1")) {
            val seg = Bech32.decodeSegwit("bc", addr) ?: throw IllegalArgumentException("endereco bech32 invalido")
            val op = if (seg.version == 0) 0x00 else 0x50 + seg.version
            return byteArrayOf(op.toByte(), seg.program.size.toByte()) + seg.program
        }
        val payload = Base58.decodeCheck(addr) ?: throw IllegalArgumentException("endereco invalido")
        val h = payload.copyOfRange(1, 21)
        return when (payload[0].toInt() and 0xFF) {
            0x00 -> p2pkh(h)
            0x05 -> p2sh(h)
            else -> throw IllegalArgumentException("prefixo de endereco desconhecido")
        }
    }

    fun p2wpkh(hash160: ByteArray): ByteArray = byteArrayOf(0x00, 0x14) + hash160
    fun p2pkh(hash160: ByteArray): ByteArray = byteArrayOf(0x76, 0xA9.toByte(), 0x14) + hash160 + byteArrayOf(0x88.toByte(), 0xAC.toByte())
    fun p2sh(hash160: ByteArray): ByteArray = byteArrayOf(0xA9.toByte(), 0x14) + hash160 + byteArrayOf(0x87.toByte())

    /** Tamanho virtual de uma saida com este script (valor 8 + varint + script). */
    fun outputVsize(script: ByteArray): Int = 8 + TxSerial.varIntSize(script.size.toLong()) + script.size
}

/** Transacao ainda sem assinaturas; calcula o sighash BIP143 e serializa com ou sem witness. */
class UnsignedTx(val version: Long, val inputs: List<TxInput>, val outputs: List<TxOutput>, val lockTime: Long) {

    private fun outpoint(i: TxInput): ByteArray = TxSerial.txidToInternal(i.txid) + TxSerial.u32le(i.vout.toLong())

    fun hashPrevouts(): ByteArray = Hash.sha256d(concat(inputs.map { outpoint(it) }))
    fun hashSequence(): ByteArray = Hash.sha256d(concat(inputs.map { TxSerial.u32le(it.sequence) }))
    fun hashOutputs(): ByteArray = Hash.sha256d(concat(outputs.map { TxSerial.serializeOutput(it) }))

    /** Sighash (SIGHASH_ALL) de uma entrada P2WPKH conforme BIP143. */
    fun sighashP2wpkh(index: Int, pubKeyHash20: ByteArray): ByteArray {
        require(pubKeyHash20.size == 20)
        val input = inputs[index]
        val scriptCode = byteArrayOf(0x19) + Script.p2pkh(pubKeyHash20)
        val preimage = TxSerial.u32le(version) +
            hashPrevouts() + hashSequence() +
            outpoint(input) + scriptCode +
            TxSerial.u64le(input.valueSat) + TxSerial.u32le(input.sequence) +
            hashOutputs() + TxSerial.u32le(lockTime) + TxSerial.u32le(SIGHASH_ALL)
        return Hash.sha256d(preimage)
    }

    /** Serializacao completa; [witnesses] nulo gera o formato legado (base para o txid). */
    fun serialize(witnesses: List<List<ByteArray>>?): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(TxSerial.u32le(version))
        if (witnesses != null) out.write(byteArrayOf(0x00, 0x01))
        out.write(TxSerial.varInt(inputs.size.toLong()))
        for (i in inputs) {
            out.write(outpoint(i))
            out.write(0x00) // scriptSig vazio (SegWit nativo)
            out.write(TxSerial.u32le(i.sequence))
        }
        out.write(TxSerial.varInt(outputs.size.toLong()))
        for (o in outputs) out.write(TxSerial.serializeOutput(o))
        if (witnesses != null) {
            require(witnesses.size == inputs.size)
            for (w in witnesses) {
                out.write(TxSerial.varInt(w.size.toLong()))
                for (item in w) {
                    out.write(TxSerial.varInt(item.size.toLong()))
                    out.write(item)
                }
            }
        }
        out.write(TxSerial.u32le(lockTime))
        return out.toByteArray()
    }

    fun txid(): String = Hash.hex(Hash.sha256d(serialize(null)).reversedArray())

    companion object {
        const val SIGHASH_ALL = 1L
        private fun concat(parts: List<ByteArray>): ByteArray {
            val out = ByteArrayOutputStream()
            parts.forEach { out.write(it) }
            return out.toByteArray()
        }
    }
}

/** Plano de envio pronto para revisao: entradas escolhidas, saidas, taxa e troco. */
class SendPlan(
    val inputs: List<TxInput>,
    val outputs: List<TxOutput>,
    val destination: String,
    val amountSat: Long,
    val feeSat: Long,
    val changeSat: Long,
    val feeRate: Int,
    val estimatedVsize: Int,
    val sendMax: Boolean,
    val lockTime: Long
) {
    val inputTotalSat: Long get() = inputs.sumOf { it.valueSat }
}

/** Escolhe entradas e monta as saidas com a taxa pedida (sat/vB). */
object SendPlanner {
    /** Entrada P2WPKH com assinatura de 72 bytes: 41 bytes base + 108 bytes de witness. */
    const val INPUT_VSIZE = 68
    /** versao 4 + locktime 4 + marcador/flag 0.5 + contadores 2, arredondado para cima. */
    const val OVERHEAD_VSIZE = 11

    fun estimateVsize(inputCount: Int, outputScripts: List<ByteArray>): Int =
        OVERHEAD_VSIZE + INPUT_VSIZE * inputCount + outputScripts.sumOf { Script.outputVsize(it) }

    fun plan(
        utxos: List<Utxo>,
        destination: String,
        amountSat: Long,
        feeRate: Int,
        changeScript: ByteArray,
        sendMax: Boolean,
        lockTime: Long
    ): SendPlan {
        require(feeRate in 1..2000) { "taxa fora do intervalo (1 a 2000 sat/vB)" }
        val destScript = Script.fromAddress(destination)
        val available = utxos.filter { it.confirmed }.sortedByDescending { it.valueSat }
        require(available.isNotEmpty()) { "nenhum saldo confirmado disponivel para gastar" }

        if (sendMax) {
            val inputs = available.map { TxInput(it.txid, it.vout, it.valueSat) }
            val total = inputs.sumOf { it.valueSat }
            val vsize = estimateVsize(inputs.size, listOf(destScript))
            val fee = vsize.toLong() * feeRate
            val amount = total - fee
            require(amount >= Script.DUST_SAT) { "saldo insuficiente para cobrir a taxa de rede" }
            return SendPlan(inputs, listOf(TxOutput(amount, destScript)), destination, amount, fee, 0, feeRate, vsize, true, lockTime)
        }

        require(amountSat >= Script.DUST_SAT) { "valor minimo e ${Script.DUST_SAT} sats" }
        val chosen = ArrayList<TxInput>()
        var sum = 0L
        for (u in available) {
            chosen.add(TxInput(u.txid, u.vout, u.valueSat))
            sum += u.valueSat
            val vsizeWithChange = estimateVsize(chosen.size, listOf(destScript, changeScript))
            val feeWithChange = vsizeWithChange.toLong() * feeRate
            if (sum >= amountSat + feeWithChange) {
                val change = sum - amountSat - feeWithChange
                if (change >= Script.DUST_SAT) {
                    val outputs = listOf(TxOutput(amountSat, destScript), TxOutput(change, changeScript))
                    return SendPlan(chosen.toList(), outputs, destination, amountSat, feeWithChange, change, feeRate, vsizeWithChange, false, lockTime)
                }
            }
            val vsizeNoChange = estimateVsize(chosen.size, listOf(destScript))
            val feeNoChange = vsizeNoChange.toLong() * feeRate
            if (sum >= amountSat + feeNoChange) {
                // troco abaixo da poeira: o que sobra vira taxa
                val fee = sum - amountSat
                return SendPlan(chosen.toList(), listOf(TxOutput(amountSat, destScript)), destination, amountSat, fee, 0, feeRate, vsizeNoChange, false, lockTime)
            }
        }
        val total = available.sumOf { it.valueSat }
        throw IllegalArgumentException("saldo confirmado insuficiente: ${total} sats disponiveis, pedido ${amountSat} sats + taxa")
    }
}

class SignedTx(val hex: String, val txid: String, val vsize: Int, val feeSat: Long)

/** Assina todas as entradas (P2WPKH da mesma chave) e serializa a transacao final. */
object TxSigner {
    const val VERSION = 2L

    fun sign(plan: SendPlan, privateKey: ByteArray, publicKey33: ByteArray): SignedTx {
        val pkh = Hash.hash160(publicKey33)
        val tx = UnsignedTx(VERSION, plan.inputs, plan.outputs, plan.lockTime)
        val witnesses = plan.inputs.indices.map { i ->
            val h = tx.sighashP2wpkh(i, pkh)
            val sig = Ecdsa.sign(privateKey, h)
            check(Ecdsa.verify(publicKey33, h, sig)) { "auto-verificacao da assinatura falhou" }
            listOf(sig.toDer() + byteArrayOf(UnsignedTx.SIGHASH_ALL.toByte()), publicKey33)
        }
        val full = tx.serialize(witnesses)
        val base = tx.serialize(null)
        val vsize = (base.size * 3 + full.size + 3) / 4
        return SignedTx(Hash.hex(full), tx.txid(), vsize, plan.feeSat)
    }
}
