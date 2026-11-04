package br.com.cavaleiros.mineradores.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.cavaleiros.mineradores.App
import br.com.cavaleiros.mineradores.data.BalanceApi
import br.com.cavaleiros.mineradores.data.MinerSettings
import br.com.cavaleiros.mineradores.data.WalletInfo
import br.com.cavaleiros.mineradores.data.WalletSource
import br.com.cavaleiros.mineradores.mining.Miner
import br.com.cavaleiros.mineradores.mining.MiningStats
import br.com.cavaleiros.mineradores.security.DeviceIntegrity
import br.com.cavaleiros.mineradores.security.IntegrityReport
import br.com.cavaleiros.mineradores.wallet.AddressValidator
import br.com.cavaleiros.mineradores.wallet.Bip39
import br.com.cavaleiros.mineradores.wallet.GeneratedWallet
import br.com.cavaleiros.mineradores.wallet.WalletFactory
import br.com.cavaleiros.mineradores.data.ChainApi
import br.com.cavaleiros.mineradores.wallet.Script
import br.com.cavaleiros.mineradores.wallet.SendPlan
import br.com.cavaleiros.mineradores.wallet.SendPlanner
import br.com.cavaleiros.mineradores.wallet.SignedTx
import br.com.cavaleiros.mineradores.wallet.SpendingKey
import br.com.cavaleiros.mineradores.wallet.SpendingKeyFactory
import br.com.cavaleiros.mineradores.wallet.TxSigner
import br.com.cavaleiros.mineradores.wallet.Utxo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class VerifyResult {
    object Match : VerifyResult()
    class Mismatch(val derivedAddress: String) : VerifyResult()
    class Invalid(val message: String) : VerifyResult()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = App.repository(app)

    val wallet: StateFlow<WalletInfo?> = repo.wallet
    val settings: StateFlow<MinerSettings> = repo.settings
    val miningStats: StateFlow<MiningStats> = Miner.engine.stats
    val bip39: Bip39 get() = repo.bip39

    // ---------------------------------------------------------------- bloqueio
    var unlocked by mutableStateOf(false)
    private var lockedAt: Long = 0L

    val lockRequired: Boolean get() = settings.value.appLock && !unlocked

    fun onAppBackground() {
        lockedAt = System.currentTimeMillis()
    }

    fun onAppForeground() {
        if (settings.value.appLock && unlocked && lockedAt > 0 && System.currentTimeMillis() - lockedAt > LOCK_GRACE_MS) {
            unlocked = false
        }
        lockedAt = 0L
    }

    // ---------------------------------------------------------------- criacao de carteira (memoria)
    var pendingWallet by mutableStateOf<GeneratedWallet?>(null)
        private set
    var generating by mutableStateOf(false)
        private set
    var generationError by mutableStateOf<String?>(null)
        private set

    fun generateWallet(passphrase: String) {
        if (generating) return
        generating = true
        generationError = null
        viewModelScope.launch {
            try {
                val w = withContext(Dispatchers.Default) { WalletFactory.create(repo.bip39, passphrase) }
                pendingWallet = w
            } catch (e: Exception) {
                generationError = "Falha ao gerar carteira: ${e.message}"
            } finally {
                generating = false
            }
        }
    }

    fun discardPendingWallet() {
        pendingWallet = null
    }

    fun confirmPendingWallet(label: String) {
        val w = pendingWallet ?: return
        repo.saveWallet(
            WalletInfo(
                address = w.address,
                zpub = w.zpub,
                masterFingerprint = w.masterFingerprint,
                derivationPath = w.derivationPath,
                label = label.ifBlank { "Carteira Cavaleiro" },
                createdAt = System.currentTimeMillis(),
                source = WalletSource.GENERATED
            )
        )
        pendingWallet = null
    }

    fun importAddress(address: String, label: String): AddressValidator.Result {
        val r = AddressValidator.validate(address)
        if (r.valid) {
            repo.saveWallet(
                WalletInfo(
                    address = r.normalized,
                    zpub = null,
                    masterFingerprint = null,
                    derivationPath = null,
                    label = label.ifBlank { "Endereco importado" },
                    createdAt = System.currentTimeMillis(),
                    source = WalletSource.IMPORTED
                )
            )
        }
        return r
    }

    fun removeWallet() {
        if (Miner.engine.isRunning) Miner.engine.stop()
        repo.clearWallet()
    }

    // ---------------------------------------------------------------- verificacao de frase (nunca persistida)
    var verifyResult by mutableStateOf<VerifyResult?>(null)
        private set
    var verifying by mutableStateOf(false)
        private set

    fun verifyMnemonic(text: String, passphrase: String) {
        val stored = wallet.value?.address
        verifying = true
        verifyResult = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val words = Bip39.splitWords(text)
                if (words.size !in Bip39.VALID_WORD_COUNTS) {
                    return@withContext VerifyResult.Invalid("Foram lidas ${words.size} palavras; esperado 24 (ou 12/15/18/21).")
                }
                val unknown = words.filter { !repo.bip39.isWord(it) }
                if (unknown.isNotEmpty()) {
                    return@withContext VerifyResult.Invalid("Palavra(s) fora da lista BIP39: ${unknown.joinToString(", ")}")
                }
                try {
                    val derived = WalletFactory.fromMnemonic(repo.bip39, words, passphrase)
                    if (stored != null && derived.address.equals(stored, ignoreCase = true)) VerifyResult.Match
                    else VerifyResult.Mismatch(derived.address)
                } catch (e: IllegalArgumentException) {
                    VerifyResult.Invalid("Checksum invalido: alguma palavra esta errada ou fora de ordem.")
                }
            }
            verifyResult = result
            verifying = false
        }
    }

    // ---------------------------------------------------------------- envio de btc
    class SendInfo(val utxos: List<Utxo>, val fees: ChainApi.Fees, val tipHeight: Int) {
        val confirmedSat: Long get() = utxos.filter { it.confirmed }.sumOf { it.valueSat }
        val pendingSat: Long get() = utxos.filter { !it.confirmed }.sumOf { it.valueSat }
    }

    class SendOutcome(val txid: String, val vsize: Int, val feeSat: Long, val amountSat: Long, val destination: String)

    private class SendException(message: String) : Exception(message)

    var sendInfo by mutableStateOf<SendInfo?>(null)
        private set
    var sendLoading by mutableStateOf(false)
        private set
    var sendPlan by mutableStateOf<SendPlan?>(null)
        private set
    var sendBusy by mutableStateOf(false)
        private set
    var sendError by mutableStateOf<String?>(null)
        private set
    var sendOutcome by mutableStateOf<SendOutcome?>(null)
        private set

    /** Consulta saidas nao gastas, taxas recomendadas e altura atual (mempool.space). */
    fun loadSendInfo() {
        val address = wallet.value?.address ?: return
        sendLoading = true
        sendError = null
        viewModelScope.launch {
            try {
                val utxos = ChainApi.utxos(address)
                val fees = ChainApi.fees()
                val tip = try { ChainApi.tipHeight() } catch (e: Exception) { 0 }
                sendInfo = SendInfo(utxos, fees, tip)
            } catch (e: Exception) {
                sendError = "Falha ao consultar a rede: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                sendLoading = false
            }
        }
    }

    /** Monta o plano (entradas, taxa, troco) para revisao. Nao toca em chaves. */
    fun prepareSend(destination: String, amountText: String, sendMax: Boolean, feeRate: Int) {
        val w = wallet.value ?: return
        val info = sendInfo
        if (info == null) {
            sendError = "Consulte o saldo antes de continuar."
            return
        }
        sendError = null
        try {
            val amount = if (sendMax) 0L else (parseBtc(amountText)
                ?: throw IllegalArgumentException("Valor invalido. Use ate 8 casas decimais, ex.: 0.00012345"))
            val changeScript = Script.fromAddress(w.address)
            sendPlan = SendPlanner.plan(info.utxos, destination.trim(), amount, feeRate, changeScript, sendMax, info.tipHeight.toLong())
        } catch (e: IllegalArgumentException) {
            sendError = e.message
        }
    }

    fun cancelPlan() {
        sendPlan = null
        sendError = null
    }

    fun resetSend() {
        sendPlan = null
        sendOutcome = null
        sendError = null
        sendBusy = false
    }

    /**
     * Deriva a chave a partir das palavras digitadas (somente em memoria), confere que gera o
     * endereco salvo, assina, transmite e zera a chave. Nada e persistido.
     */
    fun signAndBroadcast(wordsText: String, passphrase: String) {
        val plan = sendPlan ?: return
        val stored = wallet.value?.address ?: return
        sendBusy = true
        sendError = null
        viewModelScope.launch {
            val signed: SignedTx
            try {
                signed = withContext(Dispatchers.Default) { signWithWords(plan, stored, wordsText, passphrase) }
            } catch (e: Exception) {
                sendError = e.message ?: "falha ao assinar"
                sendBusy = false
                return@launch
            }
            try {
                val txid = ChainApi.broadcast(signed.hex)
                sendOutcome = SendOutcome(txid.ifBlank { signed.txid }, signed.vsize, signed.feeSat, plan.amountSat, plan.destination)
                sendPlan = null
                sendInfo = null
                if (settings.value.balanceLookup) refreshBalance()
            } catch (e: Exception) {
                sendError = "A transacao foi assinada, mas a transmissao falhou (${e.message}). Nada foi gasto; tente novamente."
            } finally {
                sendBusy = false
            }
        }
    }

    private fun signWithWords(plan: SendPlan, stored: String, wordsText: String, passphrase: String): SignedTx {
        val words = Bip39.splitWords(wordsText)
        if (words.size !in Bip39.VALID_WORD_COUNTS) throw SendException("Foram lidas ${words.size} palavras; esperado 24 (ou 12/15/18/21).")
        val unknown = words.filter { !repo.bip39.isWord(it) }
        if (unknown.isNotEmpty()) throw SendException("Palavra(s) fora da lista BIP39: ${unknown.joinToString(", ")}")
        var key: SpendingKey? = null
        try {
            key = try {
                SpendingKeyFactory.fromMnemonic(repo.bip39, words, passphrase)
            } catch (e: IllegalArgumentException) {
                throw SendException("Checksum invalido: alguma palavra esta errada ou fora de ordem.")
            }
            if (!key.address.equals(stored, ignoreCase = true)) {
                throw SendException("Essas palavras (ou a passphrase) nao correspondem a carteira deste aparelho.")
            }
            return TxSigner.sign(plan, key.privateKey, key.publicKey)
        } finally {
            key?.wipe()
        }
    }

    fun clearVerify() {
        verifyResult = null
    }

    // ---------------------------------------------------------------- configuracoes
    fun updateSettings(transform: (MinerSettings) -> MinerSettings) = repo.updateSettings(transform)

    // ---------------------------------------------------------------- saldo (opt-in)
    private val _balance = MutableStateFlow<BalanceApi.Balance?>(null)
    val balance: StateFlow<BalanceApi.Balance?> = _balance
    var balanceError by mutableStateOf<String?>(null)
        private set
    var balanceLoading by mutableStateOf(false)
        private set

    fun refreshBalance() {
        val addr = wallet.value?.address ?: return
        if (balanceLoading) return
        balanceLoading = true
        balanceError = null
        viewModelScope.launch {
            try {
                _balance.value = BalanceApi.fetch(addr)
            } catch (e: Exception) {
                balanceError = "Nao foi possivel consultar: ${e.message}"
            } finally {
                balanceLoading = false
            }
        }
    }

    // ---------------------------------------------------------------- integridade
    private val _integrity = MutableStateFlow<IntegrityReport?>(null)
    val integrity: StateFlow<IntegrityReport?> = _integrity

    fun refreshIntegrity() {
        viewModelScope.launch {
            _integrity.value = withContext(Dispatchers.IO) {
                DeviceIntegrity.check(getApplication(), repo.crypto)
            }
        }
    }

    fun wipeAll() {
        if (Miner.engine.isRunning) Miner.engine.stop()
        pendingWallet = null
        verifyResult = null
        _balance.value = null
        repo.wipeAll()
        unlocked = true
    }

    companion object {
        const val LOCK_GRACE_MS = 30_000L
    }
}

/** "0.00012345" ou "0,00012345" -> satoshis; null se invalido. */
fun parseBtc(text: String): Long? {
    val t = text.trim().replace(",", ".")
    if (t.isEmpty() || t == "." || !Regex("""\d*(\.\d{0,8})?""").matches(t)) return null
    val parts = t.split(".")
    val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
    val frac = (parts.getOrNull(1) ?: "").padEnd(8, '0').toLong()
    if (whole > 21_000_000L) return null
    return whole * 100_000_000L + frac
}
