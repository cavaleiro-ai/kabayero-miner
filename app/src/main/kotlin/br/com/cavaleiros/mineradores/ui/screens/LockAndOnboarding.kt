package br.com.cavaleiros.mineradores.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.cavaleiros.mineradores.ui.components.InstagramLink
import br.com.cavaleiros.mineradores.ui.components.MatrixButton
import br.com.cavaleiros.mineradores.ui.components.MatrixPanel
import br.com.cavaleiros.mineradores.ui.components.ShieldLogo
import br.com.cavaleiros.mineradores.ui.components.MatrixRain
import br.com.cavaleiros.mineradores.ui.components.TerminalTitle
import br.com.cavaleiros.mineradores.ui.components.WarningBox
import br.com.cavaleiros.mineradores.ui.theme.MatrixColors

@Composable
fun LockScreen(onUnlock: () -> Unit, rainEnabled: Boolean) {
    Box(Modifier.fillMaxSize()) {
        MatrixRain(Modifier.fillMaxSize(), enabled = rainEnabled, alpha = 0.35f)
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MatrixColors.Green, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            TerminalTitle("ACESSO RESTRITO")
            Spacer(Modifier.height(8.dp))
            Text(
                "Autentique-se com biometria ou PIN do aparelho para abrir o Kabayero Miner.",
                style = MaterialTheme.typography.bodyMedium,
                color = MatrixColors.GreenDark,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            MatrixButton("Desbloquear", onClick = onUnlock, filled = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun OnboardingScreen(onCreate: () -> Unit, onImport: () -> Unit, rainEnabled: Boolean) {
    Box(Modifier.fillMaxSize()) {
        MatrixRain(Modifier.fillMaxSize(), enabled = rainEnabled, alpha = 0.4f)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            ShieldLogo(size = 132.dp)
            Spacer(Modifier.height(8.dp))
            TerminalTitle("KABAYERO MINER")
            Text(
                "mineracao de bitcoin // sha-256d // carteira propria",
                style = MaterialTheme.typography.labelMedium,
                color = MatrixColors.GreenDark
            )
            Spacer(Modifier.height(28.dp))

            MatrixPanel(title = "como funciona") {
                Text(
                    "1. Gere uma carteira nova de 24 palavras (ou use um endereco que ja possui).\n" +
                        "2. O app guarda APENAS o endereco publico. As 24 palavras nunca sao salvas aqui.\n" +
                        "3. Escolha minerar solo ou em pool e inicie. As recompensas vao direto para o seu endereco.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MatrixColors.Green
                )
            }
            Spacer(Modifier.height(12.dp))
            WarningBox(
                "Realidade: um celular faz poucos milhoes de hashes por segundo, enquanto a rede faz centenas de quintilhoes. " +
                    "Em modo solo, encontrar um bloco e uma loteria extrema; em pool os ganhos sao de fracoes de centavo. " +
                    "Use como aprendizado, suporte a rede ou pela diversao de participar."
            )
            Spacer(Modifier.height(24.dp))
            MatrixButton("Criar carteira (24 palavras)", onClick = onCreate, filled = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            MatrixButton("Usar endereco que ja tenho", onClick = onImport, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Text(
                "Codigo aberto. Sem servidores proprios. Sem coleta de dados.",
                style = MaterialTheme.typography.labelSmall,
                color = MatrixColors.GreenDim,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            InstagramLink(Modifier.fillMaxWidth())
        }
    }
}
