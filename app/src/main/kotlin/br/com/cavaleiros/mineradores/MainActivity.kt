package br.com.cavaleiros.mineradores

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import br.com.cavaleiros.mineradores.service.MiningService
import br.com.cavaleiros.mineradores.ui.AppNavigation
import br.com.cavaleiros.mineradores.ui.HostActions
import br.com.cavaleiros.mineradores.ui.MainViewModel
import br.com.cavaleiros.mineradores.ui.theme.CavaleirosTheme

class MainActivity : FragmentActivity() {

    private val vm: MainViewModel by viewModels()
    private var pendingStartAfterPermission = false

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        if (pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            MiningService.start(this)
        }
    }

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = vm.onAppBackground()
        override fun onStart(owner: LifecycleOwner) = vm.onAppForeground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Barras do sistema sempre no estilo escuro (icones claros, sem scrim claro na barra de navegacao),
        // mesmo em aparelhos cujo tema do sistema e claro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)

        val actions = HostActions(
            requestUnlock = { authenticate() },
            canUseAppLock = { canAuthenticate() },
            setAppLock = { enabled -> setAppLock(enabled) },
            startMining = { startMiningWithPermission() },
            stopMining = { MiningService.stop(this) },
            openBatterySettings = { openBatteryOptimizationSettings() }
        )

        setContent {
            CavaleirosTheme {
                AppNavigation(vm = vm, actions = actions)
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ bloqueio

    private fun allowedAuthenticators(): Int =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(allowedAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS

    private fun setAppLock(enabled: Boolean) {
        if (enabled && !canAuthenticate()) {
            Toast.makeText(this, "Configure um bloqueio de tela no Android primeiro.", Toast.LENGTH_LONG).show()
            return
        }
        vm.updateSettings { it.copy(appLock = enabled) }
        if (enabled) vm.unlocked = true
    }

    private fun authenticate() {
        if (!canAuthenticate()) {
            // sem bloqueio de tela nao ha como exigir autenticacao: desliga a trava para nao prender o usuario fora do app
            vm.updateSettings { it.copy(appLock = false) }
            vm.unlocked = true
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                vm.unlocked = true
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_CANCELED) {
                    Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
                }
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Kabayero Miner")
            .setSubtitle("Autentique-se para continuar")
            .setAllowedAuthenticators(allowedAuthenticators())
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }

    // ------------------------------------------------------------------ mineracao

    private fun startMiningWithPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingStartAfterPermission = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        MiningService.start(this)
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Abra Configuracoes > Bateria e isente este app da otimizacao.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
