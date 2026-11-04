package br.com.cavaleiros.mineradores.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.ui.platform.LocalContext
import br.com.cavaleiros.mineradores.data.MinerSettings
import br.com.cavaleiros.mineradores.ui.components.MatrixSlider
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.cavaleiros.mineradores.BuildConfig
import br.com.cavaleiros.mineradores.ui.MainViewModel
import br.com.cavaleiros.mineradores.ui.components.InfoLine
import br.com.cavaleiros.mineradores.ui.components.MatrixButton
import br.com.cavaleiros.mineradores.ui.components.MatrixPanel
import br.com.cavaleiros.mineradores.ui.components.TerminalTitle
import br.com.cavaleiros.mineradores.ui.components.WarningBox
import br.com.cavaleiros.mineradores.ui.theme.MatrixColors

@Composable
fun SecurityScreen(
    vm: MainViewModel,
    canUseAppLock: Boolean,
    onToggleAppLock: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onWiped: () -> Unit
) {
    val report by vm.integrity.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var confirmWipe by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshIntegrity() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        TerminalTitle("SEGURANCA")
        Spacer(Modifier.height(10.dp))

        val r = report
        MatrixPanel(title = "integridade do aparelho", accent = if (r == null) MatrixColors.GreenDim else if (r.score >= 80) MatrixColors.Green else if (r.score >= 50) MatrixColors.Amber else MatrixColors.Red) {
            if (r == null) {
                Text("analisando...", color = MatrixColors.GreenDark, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("${r.score}", style = MaterialTheme.typography.headlineMedium, color = scoreColor(r.score), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("pontuacao de ambiente", style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDark)
                        LinearProgressIndicator(progress = { r.score / 100f }, color = scoreColor(r.score), trackColor = MatrixColors.GreenGhost, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(10.dp))
                CheckLine("Root / superusuario", !r.rooted, if (r.rooted) r.rootIndicators.joinToString("; ") else "nao detectado")
                CheckLine("Depurador conectado", !r.debuggerAttached, if (r.debuggerAttached) "um depurador esta anexado ao processo" else "nao")
                CheckLine("Build depuravel", !r.debuggableBuild, if (r.debuggableBuild) "versao de desenvolvimento" else "versao de producao")
                CheckLine("Emulador", !r.emulator, if (r.emulator) "rodando em emulador" else "aparelho fisico")
                CheckLine("Depuracao USB (ADB)", !r.adbEnabled, if (r.adbEnabled) "ativada" else "desativada")
                CheckLine("Bloqueio de tela", r.screenLockSet, if (r.screenLockSet) "configurado" else "sem PIN/biometria no aparelho")
                CheckLine("Chave no hardware seguro", r.hardwareKeystore, r.keystoreLevel)
                Spacer(Modifier.height(8.dp))
                MatrixButton("Reanalisar", onClick = { vm.refreshIntegrity() }, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(12.dp))

        MatrixPanel(title = "bloqueio do app") {
            SwitchRow("Exigir biometria ou PIN para abrir o app", settings.appLock && canUseAppLock) { v -> onToggleAppLock(v) }
            if (!canUseAppLock) {
                Text("Configure um bloqueio de tela (PIN, senha ou biometria) nas configuracoes do Android para habilitar.", color = MatrixColors.Amber, style = MaterialTheme.typography.labelSmall)
            } else {
                Text("Depois de 30 segundos em segundo plano, o app pede autenticacao novamente ao ser reaberto.", color = MatrixColors.GreenDim, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(12.dp))

        MatrixPanel(title = "protecao termica e bateria", accent = MatrixColors.Amber) {
            val tempNow = rememberBatteryTemperature()
            InfoLine(
                "temperatura da bateria agora",
                tempNow?.let { "$it°C" } ?: "--",
                valueColor = when {
                    tempNow == null -> MatrixColors.GreenDark
                    tempNow >= settings.maxBatteryTempC -> MatrixColors.Red
                    tempNow > settings.resumeBatteryTempC -> MatrixColors.Amber
                    else -> MatrixColors.GreenBright
                }
            )
            Spacer(Modifier.height(8.dp))
            Text("Pausar a mineracao a partir de ${settings.maxBatteryTempC}°C", color = MatrixColors.Green, style = MaterialTheme.typography.bodySmall)
            MatrixSlider(
                settings.maxBatteryTempC.toFloat(),
                MinerSettings.MIN_PAUSE_TEMP.toFloat()..MinerSettings.MAX_PAUSE_TEMP.toFloat(),
                MinerSettings.MAX_PAUSE_TEMP - MinerSettings.MIN_PAUSE_TEMP - 1
            ) { v -> vm.updateSettings { it.withThermalLimits(pauseC = v.roundToInt()) } }
            Text("Retomar quando esfriar ate ${settings.resumeBatteryTempC}°C", color = MatrixColors.Green, style = MaterialTheme.typography.bodySmall)
            val resumeMax = settings.maxBatteryTempC - 1
            MatrixSlider(
                settings.resumeBatteryTempC.toFloat(),
                MinerSettings.MIN_RESUME_TEMP.toFloat()..resumeMax.toFloat(),
                (resumeMax - MinerSettings.MIN_RESUME_TEMP - 1).coerceAtLeast(0)
            ) { v -> vm.updateSettings { it.withThermalLimits(resumeC = v.roundToInt()) } }
            Text(
                "Ao atingir a temperatura de pausa, o app para de calcular hashes mas mantem a conexao; so volta quando a bateria esfriar ate a temperatura de retorno. " +
                    "A diferenca entre as duas evita liga-desliga constante. Valores tipicos: pausar entre 42 e 45°C, retomar entre 36 e 38°C. Mudancas valem na hora, mesmo minerando.",
                style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDim
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow("Minerar somente com carregador conectado", settings.onlyWhileCharging) { v -> vm.updateSettings { it.copy(onlyWhileCharging = v) } }
            Text("Pausar se a bateria ficar abaixo de ${settings.minBatteryPercent}%", color = MatrixColors.Green, style = MaterialTheme.typography.bodySmall)
            MatrixSlider(settings.minBatteryPercent.toFloat(), 0f..90f, 17) { v -> vm.updateSettings { it.copy(minBatteryPercent = (v / 5f).toInt() * 5) } }
            Spacer(Modifier.height(6.dp))
            MatrixButton("Permitir rodar em segundo plano", onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth())
            Text(
                "Abre a tela do sistema para isentar o app da otimizacao de bateria. Sem isso, alguns fabricantes encerram a mineracao com a tela desligada.",
                style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDim
            )
        }
        Spacer(Modifier.height(12.dp))

        MatrixPanel(title = "modelo de seguranca") {
            Bullet("As 24 palavras sao geradas com SecureRandom (256 bits) e exibidas uma unica vez, em tela com captura bloqueada. Nunca sao gravadas, cifradas ou enviadas.")
            Bullet("O app guarda apenas dados publicos (endereco e zpub), cifrados com AES-256-GCM por chave do Android Keystore (StrongBox/TEE quando disponivel).")
            Bullet("Sem chave privada no aparelho, nao existe o que roubar: um celular comprometido pode no maximo trocar o endereco de pagamento - por isso confira-o sempre.")
            Bullet("Para enviar BTC voce digita as 24 palavras na hora: a chave e derivada em memoria, assina a transacao, e e apagada em seguida. A tela de envio bloqueia capturas e limpa as palavras ao sair.")
            Bullet("Backup em nuvem e transferencia de dados estao desativados; a area de transferencia nunca recebe palavras.")
            Bullet("Conexoes HTTP em texto puro sao bloqueadas. Stratum pode usar TLS com validacao de certificado quando o servidor oferece.")
            Bullet("Permissoes minimas: internet, servico em primeiro plano, notificacao e wake lock. Nada de contatos, localizacao, arquivos ou camera.")
            Bullet("Codigo aberto e auditavel; tudo roda localmente, sem servidores do app.")
        }
        Spacer(Modifier.height(12.dp))

        WarningBox("Se seu aparelho estiver com root ou um depurador anexado, trate as palavras exibidas como potencialmente expostas e prefira gerar a carteira em um aparelho limpo.")
        Spacer(Modifier.height(12.dp))

        MatrixPanel(title = "zona de perigo", accent = MatrixColors.Red) {
            Text(
                "Apaga endereco, configuracoes e a chave do Keystore deste app. Nao afeta seus fundos (que pertencem as 24 palavras).",
                style = MaterialTheme.typography.bodySmall, color = MatrixColors.GreenDark
            )
            Spacer(Modifier.height(8.dp))
            MatrixButton("Apagar todos os dados do app", onClick = { confirmWipe = true }, danger = true, filled = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))

        MatrixPanel(title = "sobre") {
            InfoLine("app", "Kabayero Miner")
            InfoLine("versao", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            InfoLine("algoritmo", "SHA-256d / Stratum V1")
            InfoLine("carteira", "BIP39 (24 palavras) / BIP32 / BIP84")
            InfoLine("licenca", "codigo aberto - MIT")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = MatrixColors.Panel,
            titleContentColor = MatrixColors.Red,
            textContentColor = MatrixColors.GreenDark,
            title = { Text("Apagar tudo?") },
            text = { Text("A mineracao sera parada e todos os dados locais serao destruidos. Esta acao nao pode ser desfeita.") },
            confirmButton = { TextButton(onClick = { confirmWipe = false; vm.wipeAll(); onWiped() }) { Text("Apagar", color = MatrixColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancelar", color = MatrixColors.Green) } }
        )
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> MatrixColors.Green
    score >= 50 -> MatrixColors.Amber
    else -> MatrixColors.Red
}

@Composable
private fun CheckLine(label: String, ok: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(if (ok) "[OK]" else "[!!]", color = if (ok) MatrixColors.Green else MatrixColors.Amber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = MatrixColors.Green, style = MaterialTheme.typography.bodySmall)
            Text(detail, color = if (ok) MatrixColors.GreenDark else MatrixColors.Amber, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("-", color = MatrixColors.GreenDark, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Text(text, color = MatrixColors.Green, style = MaterialTheme.typography.bodySmall)
    }
}

/** Temperatura atual da bateria (C), lida do intent sticky a cada 5 s. */
@Composable
private fun rememberBatteryTemperature(): Int? {
    val context = LocalContext.current
    var temp by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.takeIf { it > 0 }?.div(10)
            delay(5_000)
        }
    }
    return temp
}
