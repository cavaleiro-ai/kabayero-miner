package br.com.cavaleiros.mineradores.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.cavaleiros.mineradores.data.BalanceApi
import br.com.cavaleiros.mineradores.data.WalletSource
import br.com.cavaleiros.mineradores.ui.MainViewModel
import br.com.cavaleiros.mineradores.ui.components.InfoLine
import br.com.cavaleiros.mineradores.ui.components.MatrixButton
import br.com.cavaleiros.mineradores.ui.components.MatrixPanel
import br.com.cavaleiros.mineradores.ui.components.QrCode
import br.com.cavaleiros.mineradores.ui.components.TerminalTitle
import br.com.cavaleiros.mineradores.ui.components.WarningBox
import br.com.cavaleiros.mineradores.ui.theme.MatrixColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WalletScreen(vm: MainViewModel, onVerifySeed: () -> Unit, onReplaceWallet: () -> Unit, onReceive: () -> Unit, onSend: () -> Unit) {
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var showZpub by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }

    val w = wallet
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        TerminalTitle("CARTEIRA")
        Spacer(Modifier.height(10.dp))
        if (w == null) {
            WarningBox("Nenhuma carteira configurada.")
            Spacer(Modifier.height(12.dp))
            MatrixButton("Configurar carteira", onClick = onReplaceWallet, filled = true, modifier = Modifier.fillMaxWidth())
            return
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MatrixButton("Receber BTC", onClick = onReceive, filled = true, modifier = Modifier.weight(1f).height(52.dp))
            MatrixButton("Enviar BTC", onClick = onSend, filled = true, enabled = w.source == WalletSource.GENERATED, modifier = Modifier.weight(1f).height(52.dp))
        }
        if (w.source != WalletSource.GENERATED) {
            Spacer(Modifier.height(6.dp))
            Text("Envio indisponivel: este endereco foi importado sem as 24 palavras.", style = MaterialTheme.typography.labelSmall, color = MatrixColors.Amber)
        }
        Spacer(Modifier.height(12.dp))
        MatrixPanel(title = w.label) {
            Text(w.address, style = MaterialTheme.typography.bodyMedium, color = MatrixColors.GreenBright)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MatrixButton("Copiar endereco", onClick = { clipboard.setText(AnnotatedString(w.address)) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            InfoLine("origem", if (w.source == WalletSource.GENERATED) "gerada neste app (BIP39/BIP84)" else "endereco importado (somente recebimento)")
            InfoLine("criada em", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(w.createdAt)))
            w.derivationPath?.let { InfoLine("caminho", it) }
            w.masterFingerprint?.let { InfoLine("fingerprint", it) }
            InfoLine("usuario no pool", settings.username(w.address))
        }
        Spacer(Modifier.height(12.dp))

        if (w.zpub != null) {
            MatrixPanel(title = "chave publica estendida (zpub)") {
                Text(
                    "Permite acompanhar a carteira em modo somente-leitura (Sparrow, BlueWallet, Electrum). Nao permite gastar, mas revela todos os seus enderecos: compartilhe com cuidado.",
                    style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDark
                )
                Spacer(Modifier.height(8.dp))
                if (showZpub) {
                    Text(w.zpub, style = MaterialTheme.typography.bodySmall, color = MatrixColors.Green)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MatrixButton("Copiar zpub", onClick = { clipboard.setText(AnnotatedString(w.zpub)) }, modifier = Modifier.weight(1f))
                        MatrixButton("Ocultar", onClick = { showZpub = false }, modifier = Modifier.weight(1f))
                    }
                } else {
                    MatrixButton("Mostrar zpub", onClick = { showZpub = true }, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        MatrixPanel(title = "saldo on-chain (opcional)") {
            SwitchRow("Consultar saldo via mempool.space (revela seu endereco ao servico)", settings.balanceLookup) { v ->
                vm.updateSettings { it.copy(balanceLookup = v) }
            }
            if (settings.balanceLookup) {
                Spacer(Modifier.height(6.dp))
                val b = balance
                if (b != null) {
                    InfoLine("confirmado", BalanceApi.formatBtc(b.confirmedSats), valueColor = MatrixColors.GreenBright)
                    InfoLine("pendente", BalanceApi.formatBtc(b.unconfirmedSats))
                    InfoLine("transacoes", b.txCount.toString())
                }
                vm.balanceError?.let { Text(it, color = MatrixColors.Red, style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.height(8.dp))
                if (vm.balanceLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = MatrixColors.Green, modifier = Modifier.width(18.dp).height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("consultando...", color = MatrixColors.GreenDark, style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    MatrixButton("Atualizar saldo", onClick = { vm.refreshBalance() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        MatrixPanel(title = "como gastar o que minerar", accent = MatrixColors.GreenDark) {
            Text(
                if (w.source == WalletSource.GENERATED)
                    "Para enviar, toque em Enviar BTC: voce digita as 24 palavras na hora, o app assina a transacao em memoria, transmite e apaga tudo. " +
                        "Nada fica gravado. Alternativa: importe as palavras (e a passphrase, se usou) em Sparrow, BlueWallet ou Electrum " +
                        "como SegWit nativo BIP84 (m/84'/0'/0'); o primeiro endereco sera exatamente o mostrado acima."
                else
                    "Voce esta minerando para um endereco externo. Gaste pela carteira original que controla esse endereco.",
                style = MaterialTheme.typography.bodySmall, color = MatrixColors.Green
            )
        }
        Spacer(Modifier.height(12.dp))

        if (w.source == WalletSource.GENERATED) {
            MatrixButton("Verificar minhas 24 palavras", onClick = onVerifySeed, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        MatrixButton("Trocar carteira / endereco", onClick = { confirmReplace = true }, danger = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            containerColor = MatrixColors.Panel,
            titleContentColor = MatrixColors.Green,
            textContentColor = MatrixColors.GreenDark,
            title = { Text("Trocar carteira?") },
            text = {
                Text(
                    "O endereco atual sera removido deste aparelho e a mineracao sera interrompida. " +
                        "Seus fundos continuam seguros: eles pertencem as 24 palavras, nao ao app. " +
                        "Certifique-se de ter as palavras anotadas antes de continuar."
                )
            },
            confirmButton = { TextButton(onClick = { confirmReplace = false; vm.removeWallet(); onReplaceWallet() }) { Text("Remover e trocar", color = MatrixColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancelar", color = MatrixColors.Green) } }
        )
    }
}
