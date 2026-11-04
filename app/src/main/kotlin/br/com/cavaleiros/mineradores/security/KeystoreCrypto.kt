package br.com.cavaleiros.mineradores.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Criptografia AES-256-GCM com chave que nunca sai do Android Keystore
 * (StrongBox quando disponivel, senao TEE). Usada para proteger as poucas
 * informacoes persistidas pelo app (endereco publico, zpub e preferencias).
 *
 * Observacao: a frase de 24 palavras NUNCA passa por aqui. Ela nao e gravada
 * em lugar nenhum, nem cifrada.
 */
class KeystoreCrypto(private val alias: String = DEFAULT_ALIAS) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    private fun key(): SecretKey {
        val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        return generate()
    }

    private fun generate(): SecretKey {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
                return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
                    init(builder.build())
                    generateKey()
                }
            } catch (e: StrongBoxUnavailableException) {
                builder.setIsStrongBoxBacked(false)
            } catch (e: GeneralSecurityException) {
                builder.setIsStrongBoxBacked(false)
            }
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(builder.build())
            generateKey()
        }
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain)
        val out = ByteArray(1 + 1 + iv.size + ct.size)
        out[0] = VERSION
        out[1] = iv.size.toByte()
        System.arraycopy(iv, 0, out, 2, iv.size)
        System.arraycopy(ct, 0, out, 2 + iv.size, ct.size)
        return out
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > 2 && blob[0] == VERSION) { "formato de blob desconhecido" }
        val ivLen = blob[1].toInt() and 0xFF
        require(blob.size > 2 + ivLen) { "blob truncado" }
        val iv = blob.copyOfRange(2, 2 + ivLen)
        val ct = blob.copyOfRange(2 + ivLen, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    fun encryptToString(plain: String): String =
        Base64.encodeToString(encrypt(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)

    fun decryptFromString(encoded: String): String =
        String(decrypt(Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8)

    /** Descreve onde a chave esta protegida (StrongBox / TEE / software). */
    fun securityLevelLabel(): String {
        return try {
            val k = key()
            val factory = SecretKeyFactory.getInstance(k.algorithm, ANDROID_KEYSTORE)
            val info = factory.getKeySpec(k, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox (chip seguro dedicado)"
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE (ambiente de execucao confiavel)"
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> "Software (sem hardware seguro)"
                    else -> "Hardware seguro"
                }
            } else {
                @Suppress("DEPRECATION")
                if (info.isInsideSecureHardware) "Hardware seguro (TEE)" else "Software (sem hardware seguro)"
            }
        } catch (e: Exception) {
            "indisponivel"
        }
    }

    fun isHardwareBacked(): Boolean {
        return try {
            val k = key()
            val factory = SecretKeyFactory.getInstance(k.algorithm, ANDROID_KEYSTORE)
            val info = factory.getKeySpec(k, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                info.securityLevel != KeyProperties.SECURITY_LEVEL_SOFTWARE
            } else {
                @Suppress("DEPRECATION")
                info.isInsideSecureHardware
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Remove a chave (torna todos os dados cifrados irrecuperaveis). */
    @Synchronized
    fun destroyKey() {
        try { keyStore.deleteEntry(alias) } catch (_: Exception) {}
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val DEFAULT_ALIAS = "cavaleiros_mineradores_master_v1"
        private const val VERSION: Byte = 1
    }
}
