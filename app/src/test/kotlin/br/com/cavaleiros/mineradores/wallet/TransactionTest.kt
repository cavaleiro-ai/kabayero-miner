package br.com.cavaleiros.mineradores.wallet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TransactionTest {

    private fun displayTxid(rawLeHex: String) = Hash.hex(Hash.unhex(rawLeHex).reversedArray())

    /** Exemplo "Native P2WPKH" do BIP143: hashes intermediarios, sighash e assinatura da entrada 1. */
    @Test
    fun bip143NativeP2wpkhVector() {
        val tx = UnsignedTx(
            version = 1,
            inputs = listOf(
                TxInput(displayTxid("fff7f7881a8099afa6940d42d1e7f6362bec38171ea3edf433541db4e4ad969f"), 0, 625000000L, 0xFFFFFFEEL),
                TxInput(displayTxid("ef51e1b804cc89d182d279655c3aa89e815b1b309fe287d9b2b55d57b90ec68a"), 1, 600000000L, 0xFFFFFFFFL)
            ),
            outputs = listOf(
                TxOutput(112340000L, Hash.unhex("76a9148280b37df378db99f66f85c95a783a76ac7a6d5988ac")),
                TxOutput(223450000L, Hash.unhex("76a9143bde42dbee7e4dbe6a21b2d50ce2f0167faa815988ac"))
            ),
            lockTime = 17
        )
        assertEquals("96b827c8483d4e9b96712b6713a7b68d6e8003a781feba36c31143470b4efd37", Hash.hex(tx.hashPrevouts()))
        assertEquals("52b0a642eea2fb7ae638c36f6252b6750293dbe574a806984b8e4d8548339a3b", Hash.hex(tx.hashSequence()))
        assertEquals("863ef3e1a92afbfdb97f31ad0fc7683ee943e9abcf2501590ff8f6551f47e5e5", Hash.hex(tx.hashOutputs()))

        val priv = Hash.unhex("619c335025c7f4012e556c2a58b2506e30b8511b53ade95ea316fd8c3286feb9")
        val pub = Secp256k1.publicKey(priv)
        assertEquals("025476c2e83188368da1ff3e292e7acafcdb3566bb0ad253f62fc70f07aeee6357", Hash.hex(pub))
        val pkh = Hash.hash160(pub)
        assertEquals("1d0f172a0ecb48aee1be1f2687d2963ae33f71a1", Hash.hex(pkh))

        val sighash = tx.sighashP2wpkh(1, pkh)
        assertEquals("c37af31116d1b27caf68aae9e3ac82f1477929014d5b917657d0eb49478cb670", Hash.hex(sighash))

        val sig = Ecdsa.sign(priv, sighash)
        assertEquals(
            "304402203609e17b84f6a7d30c80bfa610b5b4542f32a8a0d5447a12fb1366d7f01cc44a0220573a954c4518331561406f90300e8f3358f51928d43c212a8caed02de67eebee",
            Hash.hex(sig.toDer())
        )
        assertTrue(Ecdsa.verify(pub, sighash, sig))
    }

    @Test
    fun scriptPubKeyFromAddresses() {
        // Bitcoin wiki: 16UwLL9Risc3QfPqBUvKofHmBQ7wMtjvM <-> hash160 010966776006953d5567439e5e39f86a0d273bee
        assertEquals("76a914010966776006953d5567439e5e39f86a0d273bee88ac", Hash.hex(Script.fromAddress("16UwLL9Risc3QfPqBUvKofHmBQ7wMtjvM")))
        // BIP173
        assertEquals("0014751e76e8199196d454941c45d1b3a323f1433bd6", Hash.hex(Script.fromAddress("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")))
        assertEquals("00201863143c14c5166804bd19203356da136c985678cd4d27a1b8c6329604903262", Hash.hex(Script.fromAddress("bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3")))
        // BIP350 (versao 1 com programa de 40 bytes, vetor oficial)
        assertEquals(
            "5128751e76e8199196d454941c45d1b3a323f1433bd6751e76e8199196d454941c45d1b3a323f1433bd6",
            Hash.hex(Script.fromAddress("bc1pw508d6qejxtdg4y5r3zarvary0c5xw7kw508d6qejxtdg4y5r3zarvary0c5xw7kt5nd6y"))
        )
        val p2sh = Script.fromAddress("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy")
        assertEquals(23, p2sh.size)
        assertEquals("a914", Hash.hex(p2sh.copyOfRange(0, 2)))
        assertEquals("87", Hash.hex(p2sh.copyOfRange(22, 23)))
        try {
            Script.fromAddress("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5")
            fail("checksum invalido deveria falhar")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(31, Script.outputVsize(Script.fromAddress("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")))
        assertEquals(34, Script.outputVsize(Script.fromAddress("16UwLL9Risc3QfPqBUvKofHmBQ7wMtjvM")))
        assertEquals(51, Script.outputVsize(Script.fromAddress("bc1pw508d6qejxtdg4y5r3zarvary0c5xw7kw508d6qejxtdg4y5r3zarvary0c5xw7kt5nd6y")))
        assertEquals(43, Script.outputVsize(byteArrayOf(0x51, 0x20) + ByteArray(32)))   // P2TR real (32 bytes)
    }

    private val dest = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
    private val change = Script.p2wpkh(ByteArray(20) { 0x11 })
    private val t1 = "aa".repeat(32)
    private val t2 = "bb".repeat(32)
    private val t3 = "cc".repeat(32)

    @Test
    fun plannerPicksLargestInputsAndMakesChange() {
        val utxos = listOf(Utxo(t1, 0, 100_000, true), Utxo(t2, 1, 50_000, true), Utxo(t3, 0, 30_000, false))
        val plan = SendPlanner.plan(utxos, dest, 60_000, 10, change, sendMax = false, lockTime = 0)
        assertEquals(1, plan.inputs.size)
        assertEquals(t1, plan.inputs[0].txid)
        assertEquals(141, plan.estimatedVsize)          // 11 + 68 + 31 + 31
        assertEquals(1410, plan.feeSat)
        assertEquals(38_590, plan.changeSat)
        assertEquals(2, plan.outputs.size)
        assertEquals(60_000, plan.outputs[0].valueSat)
        assertEquals(38_590, plan.outputs[1].valueSat)
        assertEquals(plan.inputTotalSat, plan.amountSat + plan.feeSat + plan.changeSat)
    }

    @Test
    fun plannerSendMaxUsesAllConfirmedInputs() {
        val utxos = listOf(Utxo(t1, 0, 100_000, true), Utxo(t2, 1, 50_000, true), Utxo(t3, 0, 30_000, false))
        val plan = SendPlanner.plan(utxos, dest, 0, 10, change, sendMax = true, lockTime = 0)
        assertEquals(2, plan.inputs.size)
        assertEquals(178, plan.estimatedVsize)          // 11 + 2*68 + 31
        assertEquals(1780, plan.feeSat)
        assertEquals(148_220, plan.amountSat)
        assertEquals(1, plan.outputs.size)
        assertEquals(0, plan.changeSat)
    }

    @Test
    fun plannerDropsDustChangeIntoFee() {
        val plan = SendPlanner.plan(listOf(Utxo(t1, 0, 70_000, true)), dest, 69_400, 1, change, false, 0)
        assertEquals(1, plan.outputs.size)
        assertEquals(600, plan.feeSat)
        assertEquals(0, plan.changeSat)
    }

    @Test
    fun plannerRejectsInsufficientOrUnconfirmedFunds() {
        try {
            SendPlanner.plan(listOf(Utxo(t1, 0, 100_000, true)), dest, 200_000, 5, change, false, 0)
            fail("deveria faltar saldo")
        } catch (_: IllegalArgumentException) {
        }
        try {
            SendPlanner.plan(listOf(Utxo(t1, 0, 100_000, false)), dest, 1_000, 5, change, false, 0)
            fail("saldo nao confirmado nao pode ser gasto")
        } catch (_: IllegalArgumentException) {
        }
        try {
            SendPlanner.plan(listOf(Utxo(t1, 0, 100_000, true)), dest, 100, 5, change, false, 0)
            fail("valor abaixo da poeira")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun signerProducesValidSegwitTransaction() {
        val priv = Hash.unhex("619c335025c7f4012e556c2a58b2506e30b8511b53ade95ea316fd8c3286feb9")
        val pub = Secp256k1.publicKey(priv)
        val ourScript = Script.p2wpkh(Hash.hash160(pub))
        val utxos = listOf(Utxo(t1, 0, 100_000, true), Utxo(t2, 3, 50_000, true))
        val plan = SendPlanner.plan(utxos, dest, 120_000, 7, ourScript, false, lockTime = 840_000)
        assertEquals(2, plan.inputs.size)
        val signed = TxSigner.sign(plan, priv, pub)

        val raw = Hash.unhex(signed.hex)
        assertEquals("02000000", Hash.hex(raw.copyOfRange(0, 4)))
        assertEquals("0001", Hash.hex(raw.copyOfRange(4, 6)))                // marcador + flag segwit
        assertEquals(840_000L, raw.copyOfRange(raw.size - 4, raw.size).let { b ->
            (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
        })
        // tamanho virtual real nao passa da estimativa (assinaturas de 70-72 bytes)
        assertTrue("vsize ${signed.vsize} vs estimativa ${plan.estimatedVsize}", signed.vsize <= plan.estimatedVsize && signed.vsize >= plan.estimatedVsize - plan.inputs.size)
        // txid = sha256d da serializacao sem witness
        val base = UnsignedTx(2, plan.inputs, plan.outputs, 840_000).serialize(null)
        assertEquals(Hash.hex(Hash.sha256d(base).reversedArray()), signed.txid)
        assertEquals(64, signed.txid.length)
        // a assinatura de cada entrada confere com o sighash recalculado
        val tx = UnsignedTx(2, plan.inputs, plan.outputs, 840_000)
        var pos = raw.size - 4
        // le os witnesses de tras para frente e simples: reconstroi a partir da serializacao esperada
        val expected = tx.serialize(plan.inputs.indices.map { i ->
            val h = tx.sighashP2wpkh(i, Hash.hash160(pub))
            listOf(Ecdsa.sign(priv, h).toDer() + byteArrayOf(1), pub)
        })
        assertArrayEquals(expected, raw)
        assertTrue(pos > 0)
    }

    @Test
    fun serializationHelpers() {
        assertEquals("fd0001", Hash.hex(TxSerial.varInt(256)))
        assertEquals("fc", Hash.hex(TxSerial.varInt(252)))
        assertEquals("fdfd00", Hash.hex(TxSerial.varInt(253)))
        assertEquals("fe00000100", Hash.hex(TxSerial.varInt(65536)))
        assertEquals("0100000000000000", Hash.hex(TxSerial.u64le(1)))
        assertEquals("fdffffff", Hash.hex(TxSerial.u32le(TxInput.SEQUENCE_RBF)))
    }
}
