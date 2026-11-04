package br.com.cavaleiros.mineradores.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.cavaleiros.mineradores.data.BalanceApi
import br.com.cavaleiros.mineradores.data.WalletSource
import br.com.cavaleiros.mineradores.ui.MainViewModel
import br.com.cavaleiros.mineradores.ui.components.InfoLine
import br.com.cavaleiros.mineradores.ui.components.MatrixButton
import br.com.cavaleiros.mineradores.ui.components.MatrixPanel
import br.com.cavaleiros.mineradores.ui.components.QrCode
import br.com.cavaleiros.mineradores.ui.components.SecureScreen
import br.com.cavaleiros.mineradores.ui.components.TerminalTitle
import br.com.cavaleiros.mineradores.ui.components.WarningBox
import br.com.cavaleiros.mineradores.ui.components.matrixTextFieldColors
import br.com.cavaleiros.mineradores.ui.parseBtc
import br.com.cavaleiros.mineradores.ui.theme.MatrixColors
import br.com.cavaleiros.mineradores.wallet.AddressValidator

@Composable
fun ReceiveScreen(vm: MainViewModel, onBack: () -> Unit) {
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val w = wallet
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        TerminalTitle("RECEBER BTC")
        Spacer(Modifier.height(10.dp))
        if (w == null) {
            WarningBox("Nenhuma carteira configurada.")
            Spacer(Modifier.height(12.dp))
            MatrixButton("Voltar", onClick = onBack, modifier = Modifier.fillMaxWidth())
            return
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            QrCode(content = "bitcoin:${w.address}", sizeDp = 260)
        }
        Spacer(Modifier.height(12.dp))
        MatrixPanel(title = "seu endereco") {
            Text(w.address, style = MaterialTheme.typography.bodyMedium, color = MatrixColors.GreenBright)
            Spacer(Modifier.height(8.dp))
            MatrixButton("Copiar endereco", onClick = { clipboard.setText(AnnotatedString(w.address)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            MatrixButton("Compartilhar endereco", onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, w.address)
                try { context.startActivity(Intent.createChooser(send, "Compartilhar endereco")) } catch (_: ActivityNotFoundException) {}
            }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        MatrixPanel(title = "como funciona") {
            Text(
                "- Qualquer pessoa, exchange ou servico pode enviar BTC para este endereco (SegWit nativo, bc1q). As recompensas do pool tambem chegam aqui.\n" +
                    "- Mostre o QR ou compartilhe o endereco. Confira sempre os primeiros e os ultimos caracteres.\n" +
                    "- Acompanhe o saldo na aba Carteira (consulta opcional) ou em qualquer explorador de blocos.\n" +
                    "- O app usa um unico endereco (m/84'/0'/0'/0/0). Reutilizar o mesmo endereco reduz a privacidade, mas e o normal em mineracao.",
                style = MaterialTheme.typography.bodySmall, color = MatrixColors.Green
            )
        }
        Spacer(Modifier.height(12.dp))
        MatrixButton("Voltar", onClick = onBack, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SendScreen(vm: MainViewModel, onBack: () -> Unit) {
    SecureScreen()
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var destination by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var sendMax by rememberSaveable { mutableStateOf(false) }
    var feeChoice by rememberSaveable { mutableIntStateOf(1) }
    var customRate by rememberSaveable { mutableStateOf("") }
    var words by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }

    // Palavras somem se o app sair de primeiro plano ou a tela for fechada.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP, lifecycleOwner = ProcessLifecycleOwner.get()) {
        words = ""
        passphrase = ""
    }
    DisposableEffect(Unit) {
        onDispose {
            words = ""
            passphrase = ""
            vm.resetSend()
        }
    }
    LaunchedEffect(Unit) { if (vm.sendInfo == null) vm.loadSendInfo() }

    val w = wallet
    val info = vm.sendInfo
    val plan = vm.sendPlan
    val outcome = vm.sendOutcome
    val fees = info?.fees
    val feeRate: Int = when (feeChoice) {
        0 -> fees?.fastest
        1 -> fees?.halfHour
        2 -> fees?.hour
        3 -> fees?.economy
        else -> customRate.toIntOrNull()
    } ?: 0
    val validation = remember(destination) { if (destination.isBlank()) null else AddressValidator.validate(destination) }
    val amountSats = remember(amount) { parseBtc(amount) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        TerminalTitle("ENVIAR BTC")
        Spacer(Modifier.height(10.dp))

        if (w == null || w.source != WalletSource.GENERATED) {
            WarningBox("O envio esta disponivel apenas para carteiras criadas neste app (com 24 palavras). Para um endereco importado, gaste pela carteira original.")
            Spacer(Modifier.height(12.dp))
            MatrixButton("Voltar", onClick = onBack, modifier = Modifier.fillMaxWidth())
            return
        }

        when {
            // ------------------------------------------------------------ resultado
            outcome != null -> {
                MatrixPanel(title = "transacao transmitida", accent = MatrixColors.Green) {
                    InfoLine("valor enviado", BalanceApi.formatBtc(outcome.amountSat), valueColor = MatrixColors.GreenBright)
                    InfoLine("taxa de rede", "${outcome.feeSat} sats (${outcome.vsize} vB)")
                    Spacer(Modifier.height(6.dp))
                    Text("destino", style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDark)
                    Text(outcome.destination, style = MaterialTheme.typography.bodySmall, color = MatrixColors.Green)
                    Spacer(Modifier.height(6.dp))
                    Text("txid", style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDark)
                    Text(outcome.txid, style = MaterialTheme.typography.bodySmall, color = MatrixColors.GreenBright)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MatrixButton("Copiar txid", onClick = { clipboard.setText(AnnotatedString(outcome.txid)) }, modifier = Modifier.weight(1f))
                        MatrixButton("Ver no explorador", onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mempool.space/tx/${outcome.txid}")))
                            } catch (_: ActivityNotFoundException) {}
                        }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A rede confirma em media a cada 10 minutos; a taxa escolhida define a prioridade. As palavras e a chave ja foram apagadas da memoria.",
                        style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDim
                    )
                }
                Spacer(Modifier.height(12.dp))
                MatrixButton("Concluir", onClick = { vm.resetSend(); onBack() }, filled = true, modifier = Modifier.fillMaxWidth())
            }

            // ------------------------------------------------------------ revisao + assinatura
            plan != null -> {
                MatrixPanel(title = "revise com atencao", accent = MatrixColors.Amber) {
                    Text("destino", style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDark)
                    Text(plan.destination, style = MaterialTheme.typography.bodyMedium, color = MatrixColors.GreenBright)
                    Spacer(Modifier.height(6.dp))
                    InfoLine("valor", BalanceApi.formatBtc(plan.amountSat), valueColor = MatrixColors.GreenBright)
                    InfoLine("taxa de rede", "${plan.feeSat} sats (${plan.feeRate} sat/vB, ~${plan.estimatedVsize} vB)")
                    InfoLine("troco (volta p/ voce)", if (plan.changeSat > 0) BalanceApi.formatBtc(plan.changeSat) else "nenhum")
                    InfoLine("total debitado", BalanceApi.formatBtc(plan.amountSat + plan.feeSat))
                    InfoLine("entradas usadas", "${plan.inputs.size}")
                    if (plan.sendMax) {
                        Spacer(Modifier.height(6.dp))
                        Text("Todo o saldo confirmado sera enviado.", style = MaterialTheme.typography.labelSmall, color = MatrixColors.Amber)
                    }
                    if (plan.feeSat * 10 > plan.amountSat) {
                        Spacer(Modifier.height(6.dp))
                        Text("Atencao: a taxa passa de 10% do valor enviado.", style = MaterialTheme.typography.labelSmall, color = MatrixColors.Amber)
                    }
                }
                Spacer(Modifier.height(12.dp))
                MatrixPanel(title = "assinar com suas 24 palavras") {
                    Text(
                        "As palavras sao usadas agora, apenas em memoria, para derivar a chave e assinar esta transacao. Nada e gravado; a chave e apagada em seguida. Captura de tela esta bloqueada.",
                        style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDim
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = words,
                        onValueChange = { words = it.lowercase() },
                        label = { Text("24 palavras separadas por espaco") },
                        minLines = 4,
                        colors = matrixTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase (se usou ao criar)") },
                        singleLine = true,
                        colors = matrixTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(12.dp))
                vm.sendError?.let {
                    WarningBox(it, danger = true)
                    Spacer(Modifier.height(12.dp))
                }
                if (vm.sendBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = MatrixColors.Green, modifier = Modifier.width(18.dp).height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("assinando e transmitindo...", color = MatrixColors.GreenDark, style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    MatrixButton(
                        "Assinar e transmitir",
                        onClick = { vm.signAndBroadcast(words, passphrase) },
                        filled = true,
                        enabled = words.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                MatrixButton("Voltar e editar", onClick = { words = ""; passphrase = ""; vm.cancelPlan() }, modifier = Modifier.fillMaxWidth())
            }

            // ------------------------------------------------------------ formulario
            else -> {
                MatrixPanel(title = "saldo disponivel") {
                    if (vm.sendLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = MatrixColors.Green, modifier = Modifier.width(18.dp).height(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("consultando mempool.space...", color = MatrixColors.GreenDark, style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (info != null) {
                        InfoLine("confirmado", BalanceApi.formatBtc(info.confirmedSat), valueColor = MatrixColors.GreenBright)
                        InfoLine("pendente (nao gastavel)", BalanceApi.formatBtc(info.pendingSat))
                        InfoLine("saidas nao gastas", "${info.utxos.size}")
                        InfoLine("altura da cadeia", if (info.tipHeight > 0) "#${info.tipHeight}" else "--")
                        Spacer(Modifier.height(8.dp))
                        MatrixButton("Atualizar", onClick = { vm.loadSendInfo() }, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("Consulta o saldo e as taxas em mempool.space (revela seu endereco ao servico).", color = MatrixColors.GreenDark, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        MatrixButton("Consultar saldo", onClick = { vm.loadSendInfo() }, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(12.dp))

                MatrixPanel(title = "destino") {
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it.trim() },
                        label = { Text("Endereco de destino (bc1..., 3..., 1...)") },
                        singleLine = true,
                        colors = matrixTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MatrixButton("Colar", onClick = { clipboard.getText()?.text?.let { destination = it.trim() } }, modifier = Modifier.weight(1f))
                        MatrixButton("Limpar", onClick = { destination = "" }, modifier = Modifier.weight(1f))
                    }
                    validation?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it.message, style = MaterialTheme.typography.labelSmall, color = if (it.valid) MatrixColors.Green else MatrixColors.Red)
                    }
                }
                Spacer(Modifier.height(12.dp))

                MatrixPanel(title = "valor") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text("Valor em BTC") },
                            singleLine = true,
                            enabled = !sendMax,
                            colors = matrixTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        MatrixButton(if (sendMax) "MAX  [x]" else "MAX  [ ]", onClick = { sendMax = !sendMax }, filled = sendMax)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            sendMax -> "Envia todo o saldo confirmado menos a taxa de rede."
                            amountSats != null -> "= $amountSats sats"
                            amount.isNotBlank() -> "valor invalido"
                            else -> "ex.: 0.0005 (minimo 546 sats)"
                        },
                        style = MaterialTheme.typography.labelSmall, color = if (amount.isNotBlank() && amountSats == null && !sendMax) MatrixColors.Red else MatrixColors.GreenDim
                    )
                }
                Spacer(Modifier.height(12.dp))

                MatrixPanel(title = "taxa de rede") {
                    val labels = listOf("rapida", "~30 min", "~1 hora", "economica")
                    val rates = listOf(fees?.fastest, fees?.halfHour, fees?.hour, fees?.economy)
                    for (row in 0 until 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (col in 0 until 2) {
                                val i = row * 2 + col
                                MatrixButton(
                                    "${labels[i]}  ${rates[i]?.let { "$it sat/vB" } ?: "--"}",
                                    onClick = { feeChoice = i },
                                    filled = feeChoice == i,
                                    enabled = rates[i] != null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customRate,
                            onValueChange = { v -> customRate = v.filter { it.isDigit() }.take(4); if (v.isNotBlank()) feeChoice = 4 },
                            label = { Text("Personalizada (sat/vB)") },
                            singleLine = true,
                            colors = matrixTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        MatrixButton("usar", onClick = { feeChoice = 4 }, filled = feeChoice == 4, enabled = customRate.toIntOrNull() != null)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (feeRate > 0) "taxa selecionada: $feeRate sat/vB" else "escolha uma taxa",
                        style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDim
                    )
                }
                Spacer(Modifier.height(12.dp))

                vm.sendError?.let {
                    WarningBox(it, danger = true)
                    Spacer(Modifier.height(12.dp))
                }
                MatrixButton(
                    "Revisar envio",
                    onClick = { vm.prepareSend(destination, amount, sendMax, feeRate) },
                    filled = true,
                    enabled = info != null && validation?.valid == true && feeRate > 0 && (sendMax || amountSats != null),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                MatrixButton("Cancelar", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
