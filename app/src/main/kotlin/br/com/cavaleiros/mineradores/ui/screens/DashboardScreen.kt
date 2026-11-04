package br.com.cavaleiros.mineradores.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.cavaleiros.mineradores.data.MinerSettings
import br.com.cavaleiros.mineradores.data.WalletInfo
import br.com.cavaleiros.mineradores.mining.ConnectionState
import br.com.cavaleiros.mineradores.mining.MiningEngine
import br.com.cavaleiros.mineradores.mining.MiningStats
import br.com.cavaleiros.mineradores.ui.MainViewModel
import br.com.cavaleiros.mineradores.ui.components.InfoLine
import br.com.cavaleiros.mineradores.ui.components.MatrixButton
import br.com.cavaleiros.mineradores.ui.components.MatrixPanel
import br.com.cavaleiros.mineradores.ui.components.PowerBar
import br.com.cavaleiros.mineradores.ui.components.StatTile
import br.com.cavaleiros.mineradores.ui.components.TerminalLog
import br.com.cavaleiros.mineradores.ui.components.TerminalTitle
import br.com.cavaleiros.mineradores.ui.components.WarningBox
import br.com.cavaleiros.mineradores.ui.components.formatUptime
import br.com.cavaleiros.mineradores.ui.components.humanizeDuration
import br.com.cavaleiros.mineradores.ui.components.shortenAddress
import br.com.cavaleiros.mineradores.ui.theme.MatrixColors
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun DashboardScreen(
    vm: MainViewModel,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val stats by vm.miningStats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val wallet by vm.wallet.collectAsStateWithLifecycle()

    // relogio de 1s para o uptime
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(stats.running) {
        while (stats.running) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        TerminalTitle("PAINEL DE MINERACAO")
        Spacer(Modifier.height(8.dp))
        StatusLine(stats, settings, wallet)
        Spacer(Modifier.height(12.dp))

        StatTile(
            label = "hashrate (10s)",
            value = if (stats.running && !stats.paused) MiningEngine.formatHashrate(stats.hashrate) else "0 H/s",
            highlight = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("shares aceitos", "${stats.sharesAccepted}", Modifier.weight(1f))
            StatTile("rejeitados", "${stats.sharesRejected}", Modifier.weight(1f))
            StatTile("blocos", "${stats.blocksFound}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("melhor share", if (stats.bestShareDifficulty > 0) MiningEngine.formatDifficulty(stats.bestShareDifficulty) else "--", Modifier.weight(1f))
            StatTile("dif. do share", if (stats.shareDifficulty > 0) MiningEngine.formatDifficulty(stats.shareDifficulty) else "--", Modifier.weight(1f))
            StatTile("tempo ativo", if (stats.running) formatUptime(stats.startedAt).also { @Suppress("UNUSED_EXPRESSION") now } else "--", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        MatrixPanel(title = "potencia do celular") {
            PowerBar(settings.power, running = stats.running, onChange = { p -> vm.updateSettings { it.copy(power = p) } })
        }
        Spacer(Modifier.height(12.dp))

        if (stats.paused) {
            WarningBox("Mineracao pausada: ${stats.pauseReason ?: "condicao do aparelho"}. Retoma automaticamente quando a condicao normalizar.")
            Spacer(Modifier.height(12.dp))
        }
        stats.lastError?.let {
            if (stats.connection == ConnectionState.ERROR) {
                WarningBox("Erro: $it", danger = true)
                Spacer(Modifier.height(12.dp))
            }
        }

        if (stats.running) {
            MatrixButton("Parar mineracao", onClick = onStop, danger = true, filled = true, modifier = Modifier.fillMaxWidth().height(52.dp))
        } else {
            MatrixButton(
                "Iniciar mineracao",
                onClick = onStart,
                enabled = wallet != null,
                filled = true,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
        Spacer(Modifier.height(14.dp))

        MatrixPanel(title = "rede e trabalho") {
            InfoLine("modo", "${settings.mode.label} - ${settings.resolvePool().label}")
            InfoLine("bloco atual", stats.blockHeight?.let { "#$it" } ?: "--")
            InfoLine("dif. da rede", if (stats.networkDifficulty > 0) MiningEngine.formatDifficulty(stats.networkDifficulty) else "--")
            InfoLine("job", stats.currentJobId ?: "--")
            InfoLine("hash anterior", stats.prevHash?.let { shortenAddress(it, 12, 12) } ?: "--")
            InfoLine("potencia", "${settings.power}% (${settings.cpuLoad().label}, ${MinerSettings.maxThreads()} nucleos)")
            InfoLine("hashes totais", formatBigNumber(stats.totalHashes))
            InfoLine("media geral", MiningEngine.formatHashrate(stats.hashrateAverage))
        }
        Spacer(Modifier.height(12.dp))

        RealityCheck(stats, settings)
        Spacer(Modifier.height(12.dp))

        Text("// terminal", style = MaterialTheme.typography.labelLarge, color = MatrixColors.GreenDark)
        Spacer(Modifier.height(6.dp))
        TerminalLog(stats.log, modifier = Modifier.fillMaxWidth().height(260.dp))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusLine(stats: MiningStats, settings: MinerSettings, wallet: WalletInfo?) {
    val (color, label) = when {
        !stats.running -> MatrixColors.GreenDim to "parado"
        stats.paused -> MatrixColors.Amber to "pausado"
        stats.connection == ConnectionState.AUTHORIZED -> MatrixColors.Green to "minerando - ${stats.poolAddress}"
        stats.connection == ConnectionState.ERROR -> MatrixColors.Red to "erro de conexao"
        else -> MatrixColors.Amber to "${stats.connection.label} - ${stats.poolAddress}"
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "pagamentos -> ${wallet?.address?.let { shortenAddress(it, 14, 10) } ?: "nenhuma carteira"}  [${settings.workerName}]",
            style = MaterialTheme.typography.labelSmall,
            color = MatrixColors.GreenDark
        )
    }
}

@Composable
private fun RealityCheck(stats: MiningStats, settings: MinerSettings) {
    val hashrate = if (stats.running && stats.hashrate > 0) stats.hashrate else stats.hashrateAverage
    val netDiff = stats.networkDifficulty
    MatrixPanel(title = "expectativa honesta") {
        if (hashrate <= 0 || netDiff <= 0) {
            Text(
                "Inicie a mineracao para calcular, com base no seu hashrate real e na dificuldade atual da rede, " +
                    "o tempo esperado ate encontrar um bloco.",
                style = MaterialTheme.typography.bodySmall,
                color = MatrixColors.GreenDark
            )
        } else {
            val expectedSeconds = netDiff * 4294967296.0 / hashrate
            val perDay = hashrate * 86400.0 / (netDiff * 4294967296.0)
            InfoLine("tempo esperado p/ bloco (solo)", humanizeDuration(expectedSeconds), valueColor = MatrixColors.Amber)
            InfoLine("chance por dia", if (perDay > 0) "1 em ${formatBigNumber((1 / perDay).toLong())}" else "--", valueColor = MatrixColors.Amber)
            Spacer(Modifier.height(4.dp))
            Text(
                if (settings.mode.name == "SOLO")
                    "Solo e uma loteria: ou voce ganha o bloco inteiro (~3,125 BTC + taxas) ou nada. A chance acima e a realidade de um celular."
                else
                    "Em pool a recompensa e proporcional ao seu hashrate: para um celular isso representa fracoes de centavo por dia.",
                style = MaterialTheme.typography.labelSmall,
                color = MatrixColors.GreenDim
            )
        }
    }
}

fun formatBigNumber(n: Long): String = when {
    n >= 1_000_000_000_000L -> String.format(Locale.US, "%.2f tri", n / 1e12)
    n >= 1_000_000_000L -> String.format(Locale.US, "%.2f bi", n / 1e9)
    n >= 1_000_000L -> String.format(Locale.US, "%.2f mi", n / 1e6)
    n >= 1_000L -> String.format(Locale.US, "%.1f mil", n / 1e3)
    else -> n.toString()
}
