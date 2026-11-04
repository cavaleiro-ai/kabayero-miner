package br.com.cavaleiros.mineradores.security

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences com valores cifrados via [KeystoreCrypto].
 * As chaves (nomes) ficam em claro; os valores sao AES-GCM com chave no Keystore.
 */
class SecureStore(context: Context, private val crypto: KeystoreCrypto = KeystoreCrypto()) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun putString(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, crypto.encryptToString(value)).apply()
        }
    }

    fun getString(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            crypto.decryptFromString(raw)
        } catch (e: Exception) {
            // chave invalidada ou dado corrompido: descarta para nao travar o app
            prefs.edit().remove(key).apply()
            null
        }
    }

    fun putBoolean(key: String, value: Boolean) = putString(key, if (value) "1" else "0")
    fun getBoolean(key: String, default: Boolean): Boolean = when (getString(key)) {
        "1" -> true
        "0" -> false
        else -> default
    }

    fun putLong(key: String, value: Long) = putString(key, value.toString())
    fun getLong(key: String, default: Long): Long = getString(key)?.toLongOrNull() ?: default

    fun contains(key: String): Boolean = prefs.contains(key)
    fun remove(key: String) = prefs.edit().remove(key).apply()

    fun clearAll() {
        prefs.edit().clear().commit()
    }

    fun crypto(): KeystoreCrypto = crypto

    companion object {
        private const val FILE = "cavaleiros_secure_v1"
    }
}
