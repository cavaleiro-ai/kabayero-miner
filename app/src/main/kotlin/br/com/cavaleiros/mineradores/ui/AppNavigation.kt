package br.com.cavaleiros.mineradores.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.cavaleiros.mineradores.ui.components.MatrixRain
import br.com.cavaleiros.mineradores.ui.screens.ConfigScreen
import br.com.cavaleiros.mineradores.ui.screens.ConfirmWordsScreen
import br.com.cavaleiros.mineradores.ui.screens.CreateWalletScreen
import br.com.cavaleiros.mineradores.ui.screens.DashboardScreen
import br.com.cavaleiros.mineradores.ui.screens.ImportAddressScreen
import br.com.cavaleiros.mineradores.ui.screens.LockScreen
import br.com.cavaleiros.mineradores.ui.screens.OnboardingScreen
import br.com.cavaleiros.mineradores.ui.screens.ReceiveScreen
import br.com.cavaleiros.mineradores.ui.screens.SendScreen
import br.com.cavaleiros.mineradores.ui.screens.SecurityScreen
import br.com.cavaleiros.mineradores.ui.screens.ShowWordsScreen
import br.com.cavaleiros.mineradores.ui.screens.VerifySeedScreen
import br.com.cavaleiros.mineradores.ui.screens.WalletScreen
import br.com.cavaleiros.mineradores.ui.theme.MatrixColors

/** Acoes que precisam da Activity (biometria, permissoes, servico). */
class HostActions(
    val requestUnlock: () -> Unit,
    val canUseAppLock: () -> Boolean,
    val setAppLock: (Boolean) -> Unit,
    val startMining: () -> Unit,
    val stopMining: () -> Unit,
    val openBatterySettings: () -> Unit
)

object Routes {
    const val LOCK = "lock"
    const val ONBOARDING = "onboarding"
    const val CREATE = "create"
    const val WORDS = "words"
    const val CONFIRM = "confirm"
    const val IMPORT = "import"
    const val HOME = "home"
    const val VERIFY = "verify"
    const val RECEIVE = "receive"
    const val SEND = "send"
}

@Composable
fun AppNavigation(vm: MainViewModel, actions: HostActions) {
    val navController = rememberNavController()
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.miningStats.collectAsStateWithLifecycle()
    val rain = settings.matrixRain && !stats.running

    val start = when {
        vm.lockRequired -> Routes.LOCK
        wallet == null -> Routes.ONBOARDING
        else -> Routes.HOME
    }

    // Se o app for bloqueado enquanto aberto (volta do segundo plano), vai para a tela de bloqueio.
    LaunchedEffect(vm.unlocked, settings.appLock) {
        if (vm.lockRequired) {
            navController.navigate(Routes.LOCK) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LOCK) {
            LockScreen(onUnlock = actions.requestUnlock, rainEnabled = rain)
            LaunchedEffect(vm.unlocked) {
                if (vm.unlocked) {
                    navController.navigate(if (vm.wallet.value == null) Routes.ONBOARDING else Routes.HOME) { popUpTo(0) { inclusive = true } }
                }
            }
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onCreate = { navController.navigate(Routes.CREATE) },
                onImport = { navController.navigate(Routes.IMPORT) },
                rainEnabled = rain
            )
        }
        composable(Routes.CREATE) {
            CreateWalletScreen(
                vm = vm,
                onGenerated = { navController.navigate(Routes.WORDS) { popUpTo(Routes.CREATE) { inclusive = true } } },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.WORDS) {
            ShowWordsScreen(
                vm = vm,
                onContinue = { navController.navigate(Routes.CONFIRM) },
                onAbort = { navController.navigate(homeOrOnboarding(vm)) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(Routes.CONFIRM) {
            ConfirmWordsScreen(
                vm = vm,
                onDone = { navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                onAbort = { navController.navigate(homeOrOnboarding(vm)) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(Routes.IMPORT) {
            ImportAddressScreen(
                vm = vm,
                onDone = { navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.VERIFY) {
            VerifySeedScreen(vm = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.RECEIVE) {
            ReceiveScreen(vm = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.SEND) {
            SendScreen(vm = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.HOME) {
            HomeScreen(vm, actions, navController, rain)
        }
    }
}

/** Ao abortar um fluxo, volta para o painel se ja existe carteira salva; senao, para o inicio. */
private fun homeOrOnboarding(vm: MainViewModel): String =
    if (vm.wallet.value == null) Routes.ONBOARDING else Routes.HOME

private enum class Tab(val label: String, val icon: ImageVector) {
    MINE("Minerar", Icons.Default.Memory),
    WALLET("Carteira", Icons.Default.AccountBalanceWallet),
    CONFIG("Config", Icons.Default.Tune),
    SECURITY("Seguranca", Icons.Default.Security)
}

@Composable
private fun HomeScreen(vm: MainViewModel, actions: HostActions, navController: NavHostController, rain: Boolean) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.values()[tabIndex]

    Scaffold(
        containerColor = MatrixColors.Black,
        bottomBar = {
            NavigationBar(containerColor = MatrixColors.Panel, tonalElevation = 0.dp) {
                Tab.values().forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = i == tabIndex,
                        onClick = { tabIndex = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MatrixColors.Black,
                            selectedTextColor = MatrixColors.Green,
                            indicatorColor = MatrixColors.Green,
                            unselectedIconColor = MatrixColors.GreenDark,
                            unselectedTextColor = MatrixColors.GreenDark
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MatrixRain(Modifier.fillMaxSize(), enabled = rain, alpha = 0.22f)
            when (tab) {
                Tab.MINE -> DashboardScreen(vm, onStart = actions.startMining, onStop = actions.stopMining)
                Tab.WALLET -> WalletScreen(
                    vm,
                    onVerifySeed = { navController.navigate(Routes.VERIFY) },
                    onReplaceWallet = { navController.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } } },
                    onReceive = { navController.navigate(Routes.RECEIVE) },
                    onSend = { navController.navigate(Routes.SEND) }
                )
                Tab.CONFIG -> ConfigScreen(vm)
                Tab.SECURITY -> SecurityScreen(
                    vm,
                    canUseAppLock = actions.canUseAppLock(),
                    onToggleAppLock = actions.setAppLock,
                    onOpenBatterySettings = actions.openBatterySettings,
                    onWiped = { navController.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } } }
                )
            }
        }
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
