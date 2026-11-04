package br.com.cavaleiros.mineradores.data

import android.content.Context
import br.com.cavaleiros.mineradores.R
import br.com.cavaleiros.mineradores.security.KeystoreCrypto
import br.com.cavaleiros.mineradores.security.SecureStore
import br.com.cavaleiros.mineradores.wallet.Bip39
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fonte unica de dados persistidos do app. Tudo passa pelo [SecureStore]
 * (AES-GCM com chave no Android Keystore).
 */
class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    val crypto = KeystoreCrypto()
    private val store = SecureStore(appContext, crypto)

    val bip39: Bip39 by lazy {
        appContext.resources.openRawResource(R.raw.bip39_english).use { Bip39.load(it) }
    }

    private val _wallet = MutableStateFlow(WalletInfo.fromJson(store.getString(KEY_WALLET)))
    val wallet: StateFlow<WalletInfo?> = _wallet

    private val _settings = MutableStateFlow(MinerSettings.fromJson(store.getString(KEY_SETTINGS)))
    val settings: StateFlow<MinerSettings> = _settings

    val onboardingDone: Boolean get() = store.getBoolean(KEY_ONBOARDED, false)

    fun saveWallet(info: WalletInfo) {
        store.putString(KEY_WALLET, info.toJson())
        store.putBoolean(KEY_ONBOARDED, true)
        _wallet.value = info
    }

    fun clearWallet() {
        store.remove(KEY_WALLET)
        _wallet.value = null
    }

    fun updateSettings(transform: (MinerSettings) -> MinerSettings) {
        val next = transform(_settings.value)
        store.putString(KEY_SETTINGS, next.toJson())
        _settings.value = next
    }

    /** Apaga tudo e destroi a chave do Keystore. */
    fun wipeAll() {
        store.clearAll()
        crypto.destroyKey()
        _wallet.value = null
        _settings.value = MinerSettings()
    }

    companion object {
        private const val KEY_WALLET = "wallet"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_ONBOARDED = "onboarded"
    }
}
