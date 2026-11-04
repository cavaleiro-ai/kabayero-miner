package br.com.cavaleiros.mineradores.security

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.provider.Settings
import java.io.File

data class IntegrityReport(
    val rootIndicators: List<String>,
    val debuggableBuild: Boolean,
    val debuggerAttached: Boolean,
    val emulator: Boolean,
    val adbEnabled: Boolean,
    val developerOptions: Boolean,
    val screenLockSet: Boolean,
    val keystoreLevel: String,
    val hardwareKeystore: Boolean
) {
    val rooted: Boolean get() = rootIndicators.isNotEmpty()

    /** 0..100, apenas orientativo. */
    val score: Int
        get() {
            var s = 100
            if (rooted) s -= 40
            if (debuggerAttached) s -= 20
            if (debuggableBuild) s -= 10
            if (emulator) s -= 10
            if (adbEnabled) s -= 10
            if (!screenLockSet) s -= 15
            if (!hardwareKeystore) s -= 10
            return s.coerceIn(0, 100)
        }
}

/**
 * Verificacoes heuristicas de integridade do dispositivo. Nao bloqueiam o uso;
 * servem para alertar o usuario sobre o ambiente em que o app esta rodando.
 */
object DeviceIntegrity {

    private val SU_PATHS = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/su",
        "/system/bin/.ext/.su", "/system/usr/we-need-root/su-backup", "/system/xbin/mu",
        "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su", "/system/sd/xbin/su",
        "/system/bin/failsafe/su", "/system/app/Superuser.apk", "/sbin/.magisk", "/data/adb/magisk"
    )

    private val ROOT_PACKAGES = listOf(
        "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.koushikdutta.superuser",
        "com.noshufou.android.su", "me.weishu.kernelsu"
    )

    fun check(context: Context, crypto: KeystoreCrypto): IntegrityReport {
        val indicators = ArrayList<String>()
        for (p in SU_PATHS) {
            try { if (File(p).exists()) indicators.add("arquivo $p") } catch (_: Exception) {}
        }
        val tags = Build.TAGS
        if (tags != null && tags.contains("test-keys")) indicators.add("build assinada com test-keys")
        val pm = context.packageManager
        for (pkg in ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                indicators.add("app de root instalado ($pkg)")
            } catch (_: PackageManager.NameNotFoundException) {
            } catch (_: Exception) {}
        }

        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val debugger = Debug.isDebuggerConnected() || Debug.waitingForDebugger()

        val adb = try { Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 } catch (_: Exception) { false }
        val dev = try { Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 } catch (_: Exception) { false }

        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val lock = keyguard.isDeviceSecure

        return IntegrityReport(
            rootIndicators = indicators,
            debuggableBuild = debuggable,
            debuggerAttached = debugger,
            emulator = isEmulator(),
            adbEnabled = adb,
            developerOptions = dev,
            screenLockSet = lock,
            keystoreLevel = crypto.securityLevelLabel(),
            hardwareKeystore = crypto.isHardwareBacked()
        )
    }

    fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val product = Build.PRODUCT ?: ""
        val hw = Build.HARDWARE ?: ""
        return fp.startsWith("generic") || fp.startsWith("unknown") ||
            model.contains("google_sdk") || model.contains("Emulator") || model.contains("Android SDK built for") ||
            model.contains("sdk_gphone") || product.contains("sdk") || product.contains("emulator") ||
            hw.contains("goldfish") || hw.contains("ranchu") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
    }

    fun isLowRamDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.isLowRamDevice
    }
}
