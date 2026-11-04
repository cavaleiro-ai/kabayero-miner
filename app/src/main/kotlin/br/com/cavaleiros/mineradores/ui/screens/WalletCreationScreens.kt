package br.com.cavaleiros.mineradores.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import br.com.cavaleiros.mineradores.ui.MainViewModel
import br.com.cavaleiros.mineradores.ui.components.MatrixButton
import br.com.cavaleiros.mineradores.ui.components.MatrixPanel
import br.com.cavaleiros.mineradores.ui.components.SecureScreen
import br.com.cavaleiros.mineradores.ui.components.TerminalTitle
import br.com.cavaleiros.mineradores.ui.components.WarningBox
import br.com.cavaleiros.mineradores.ui.components.matrixTextFieldColors
import br.com.cavaleiros.mineradores.ui.theme.MatrixColors
import br.com.cavaleiros.mineradores.wallet.Bip39
import java.security.SecureRandom

/** Passo 1: explicacao + passphrase opcional + gerar. */
@Composable
fun CreateWalletScreen(vm: MainViewModel, onGenerated: () -> Unit, onBack: () -> Unit) {
    SecureScreen()
    var usePassphrase by rememberSaveable { mutableStateOf(false) }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var passphrase2 by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm.pendingWallet) {
        if (vm.pendingWallet != null) onGenerated()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TerminalTitle("NOVA CARTEIRA")
        Spacer(Modifier.height(12.dp))
        MatrixPanel(title = "o que vai acontecer") {
            Text(
                "- 256 bits de entropia do gerador seguro do Android viram 24 palavras (BIP39).\n" +
                    "- Dessas palavras derivamos o endereco SegWit nativo bc1q... (BIP84, m/84'/0'/0'/0/0).\n" +
                    "- Somente o endereco e a chave publica estendida (zpub) ficam no aparelho, cifrados pelo Keystore.\n" +
                    "- As 24 palavras aparecem UMA vez. Anote em papel. Elas nao sao salvas nem enviadas a lugar algum.",
                style = MaterialTheme.typography.bodySmall,
                color = MatrixColors.Green
            )
        }
        Spacer(Modifier.height(12.dp))
        WarningBox(
            "Quem tiver as 24 palavras controla os fundos. Nao fotografe, nao digite em sites, nao guarde em nuvem. " +
                "Se perder as palavras, as moedas mineradas ficam inacessiveis para sempre.",
            danger = true
        )
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = usePassphrase,
                onCheckedChange = { usePassphrase = it; if (!it) { passphrase = ""; passphrase2 = "" } },
                colors = CheckboxDefaults.colors(checkedColor = MatrixColors.Green, uncheckedColor = MatrixColors.GreenDark, checkmarkColor = MatrixColors.Black)
            )
            Text("Usar passphrase (25a palavra) - avancado", style = MaterialTheme.typography.bodySmall, color = MatrixColors.Green)
        }
        if (usePassphrase) {
            Text(
                "A passphrase altera completamente a carteira. Sem ela, as 24 palavras abrem OUTRA carteira (vazia). Guarde-a separada das palavras.",
                style = MaterialTheme.typography.bodySmall,
                color = MatrixColors.Amber
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("Passphrase") },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                trailingIcon = {
                    IconButton(onClick = { show = !show }) {
                        Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = MatrixColors.GreenDark)
                    }
                },
                colors = matrixTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = passphrase2,
                onValueChange = { passphrase2 = it },
                label = { Text("Repita a passphrase") },
                singleLine = true,
                isError = passphrase2.isNotEmpty() && passphrase2 != passphrase,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                colors = matrixTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(20.dp))
        vm.generationError?.let {
            WarningBox(it, danger = true)
            Spacer(Modifier.height(12.dp))
        }
        val canGenerate = !vm.generating && (!usePassphrase || (passphrase.isNotEmpty() && passphrase == passphrase2))
        if (vm.generating) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = MatrixColors.Green, modifier = Modifier.width(24.dp).height(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("gerando entropia e derivando chaves...", color = MatrixColors.GreenDark, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            MatrixButton(
                "Gerar 24 palavras",
                onClick = { vm.generateWallet(if (usePassphrase) passphrase else "") },
                enabled = canGenerate,
                filled = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
        MatrixButton("Voltar", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

/** Passo 2: exibicao das 24 palavras (FLAG_SECURE, descarta ao sair do app). */
@Composable
fun ShowWordsScreen(vm: MainViewModel, onContinue: () -> Unit, onAbort: () -> Unit) {
    SecureScreen()
    val wallet = vm.pendingWallet
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var confirmAbort by remember { mutableStateOf(false) }

    // Observa o ciclo de vida do PROCESSO (nao o da rota): o NavBackStackEntry
    // tambem emite ON_STOP ao navegar para a proxima tela, o que descartaria a
    // carteira no meio do fluxo. Aqui so dispara quando o app vai para segundo plano.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP, lifecycleOwner = ProcessLifecycleOwner.get()) {
        vm.discardPendingWallet()
        onAbort()
    }
    BackHandler { confirmAbort = true }

    if (wallet == null) {
        LaunchedEffect(Unit) { onAbort() }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TerminalTitle("SUAS 24 PALAVRAS")
        Spacer(Modifier.height(6.dp))
        Text(
            "Anote na ordem exata, em papel. Esta tela bloqueia capturas e as palavras somem ao sair do app.",
            style = MaterialTheme.typography.bodySmall,
            color = MatrixColors.Amber
        )
        Spacer(Modifier.height(14.dp))
        WordGrid(wallet.words)
        Spacer(Modifier.height(14.dp))
        MatrixPanel(title = "endereco derivado") {
            Text(wallet.address, style = MaterialTheme.typography.bodySmall, color = MatrixColors.GreenBright)
            Spacer(Modifier.height(4.dp))
            Text("caminho ${wallet.derivationPath} - fingerprint ${wallet.masterFingerprint}", style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDark)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = acknowledged,
                onCheckedChange = { acknowledged = it },
                colors = CheckboxDefaults.colors(checkedColor = MatrixColors.Green, uncheckedColor = MatrixColors.GreenDark, checkmarkColor = MatrixColors.Black)
            )
            Text("Anotei as 24 palavras em papel e entendo que elas nao ficam no celular.", style = MaterialTheme.typography.bodySmall, color = MatrixColors.Green)
        }
        Spacer(Modifier.height(12.dp))
        MatrixButton("Continuar para confirmacao", onClick = onContinue, enabled = acknowledged, filled = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        MatrixButton("Cancelar e descartar", onClick = { confirmAbort = true }, danger = true, modifier = Modifier.fillMaxWidth())
    }

    if (confirmAbort) {
        AlertDialog(
            onDismissRequest = { confirmAbort = false },
            containerColor = MatrixColors.Panel,
            titleContentColor = MatrixColors.Green,
            textContentColor = MatrixColors.GreenDark,
            title = { Text("Descartar carteira?") },
            text = { Text("As palavras serao apagadas da memoria e esta carteira nunca mais podera ser recuperada.") },
            confirmButton = { TextButton(onClick = { confirmAbort = false; vm.discardPendingWallet(); onAbort() }) { Text("Descartar", color = MatrixColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmAbort = false }) { Text("Voltar", color = MatrixColors.Green) } }
        )
    }
}

@Composable
private fun WordGrid(words: List<String>) {
    val half = (words.size + 1) / 2
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (col in 0 until 2) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until half) {
                    val idx = col * half + i
                    if (idx >= words.size) break
                    WordChip(idx + 1, words[idx])
                }
            }
        }
    }
}

@Composable
private fun WordChip(number: Int, word: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MatrixColors.PanelAlt, RoundedCornerShape(3.dp))
            .border(1.dp, MatrixColors.GreenDim, RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(String.format("%02d", number), style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDark)
        Spacer(Modifier.width(8.dp))
        Text(word, style = MaterialTheme.typography.bodyMedium, color = MatrixColors.GreenBright, fontWeight = FontWeight.Bold)
    }
}

/** Passo 3: confirmacao de 4 palavras aleatorias + nome da carteira. */
@Composable
fun ConfirmWordsScreen(vm: MainViewModel, onDone: () -> Unit, onAbort: () -> Unit) {
    SecureScreen()
    val wallet = vm.pendingWallet
    val indices = rememberSaveable { pickIndices(wallet?.words?.size ?: 24, 4) }
    val answers = remember { mutableStateOf(List(indices.size) { "" }) }
    var label by rememberSaveable { mutableStateOf("") }
    var attemptedWrong by remember { mutableStateOf(false) }
    // Depois de salvar, pendingWallet vira null; sem esta flag a recomposicao
    // cairia no ramo "carteira ausente" e abortaria o fluxo ja concluido.
    var completed by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP, lifecycleOwner = ProcessLifecycleOwner.get()) {
        if (!completed && vm.pendingWallet != null) {
            vm.discardPendingWallet()
            onAbort()
        }
    }
    BackHandler(enabled = !completed) { vm.discardPendingWallet(); onAbort() }

    if (wallet == null) {
        if (!completed) LaunchedEffect(Unit) { onAbort() }
        return
    }

    val allCorrect = indices.indices.all { i ->
        Bip39.normalizeWord(answers.value[i]) == wallet.words[indices[i]]
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TerminalTitle("CONFIRME SUAS PALAVRAS")
        Spacer(Modifier.height(6.dp))
        Text(
            "Digite as palavras pedidas para provar que anotou corretamente.",
            style = MaterialTheme.typography.bodySmall,
            color = MatrixColors.GreenDark
        )
        Spacer(Modifier.height(14.dp))
        indices.forEachIndexed { i, wordIndex ->
            val value = answers.value[i]
            val ok = value.isNotEmpty() && Bip39.normalizeWord(value) == wallet.words[wordIndex]
            val suggestions = if (value.length >= 2 && !ok) vm.bip39.suggestions(value, 4) else emptyList()
            OutlinedTextField(
                value = value,
                onValueChange = { v -> answers.value = answers.value.toMutableList().also { it[i] = v.lowercase().trim() } },
                label = { Text("Palavra #${wordIndex + 1}") },
                singleLine = true,
                isError = attemptedWrong && !ok,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii, imeAction = if (i == indices.lastIndex) ImeAction.Done else ImeAction.Next, autoCorrectEnabled = false),
                supportingText = {
                    when {
                        ok -> Text("ok", color = MatrixColors.Green)
                        suggestions.isNotEmpty() -> Text(suggestions.joinToString("  "), color = MatrixColors.GreenDim)
                        else -> {}
                    }
                },
                colors = matrixTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(40) },
            label = { Text("Nome da carteira (opcional)") },
            singleLine = true,
            colors = matrixTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        if (attemptedWrong && !allCorrect) {
            WarningBox("Alguma palavra nao confere. Volte e revise sua anotacao antes de continuar.", danger = true)
            Spacer(Modifier.height(12.dp))
        }
        MatrixButton(
            "Confirmar e salvar endereco",
            onClick = {
                if (allCorrect) {
                    completed = true
                    vm.confirmPendingWallet(label)
                    onDone()
                } else attemptedWrong = true
            },
            filled = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        MatrixButton("Cancelar", onClick = { vm.discardPendingWallet(); onAbort() }, danger = true, modifier = Modifier.fillMaxWidth())
    }
}

private fun pickIndices(total: Int, count: Int): List<Int> {
    val rnd = SecureRandom()
    val set = LinkedHashSet<Int>()
    while (set.size < count.coerceAtMost(total)) set.add(rnd.nextInt(total))
    return set.sorted()
}

/** Importacao de endereco existente (somente observacao/recebimento). */
@Composable
fun ImportAddressScreen(vm: MainViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    val validation = remember(address) { if (address.isBlank()) null else br.com.cavaleiros.mineradores.wallet.AddressValidator.validate(address) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TerminalTitle("USAR ENDERECO EXISTENTE")
        Spacer(Modifier.height(8.dp))
        Text(
            "Cole um endereco Bitcoin (bc1q..., bc1p..., 3... ou 1...). As recompensas de mineracao serao pagas nele. " +
                "O app nunca pede chaves privadas ou frases para esta opcao.",
            style = MaterialTheme.typography.bodySmall,
            color = MatrixColors.GreenDark
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it.trim() },
            label = { Text("Endereco Bitcoin (mainnet)") },
            singleLine = false,
            minLines = 2,
            isError = validation != null && !validation.valid,
            supportingText = {
                validation?.let { Text(it.message, color = if (it.valid) MatrixColors.Green else MatrixColors.Red) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            colors = matrixTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(40) },
            label = { Text("Nome (opcional)") },
            singleLine = true,
            colors = matrixTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        WarningBox("Confira o endereco caractere por caractere. Um erro de digitacao pode enviar recompensas para um endereco que nao e seu (o checksum detecta a maioria, mas nao todos os casos de troca intencional).")
        Spacer(Modifier.height(16.dp))
        MatrixButton(
            "Salvar endereco",
            onClick = { if (vm.importAddress(address, label).valid) onDone() },
            enabled = validation?.valid == true,
            filled = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        MatrixButton("Voltar", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

/** Ferramenta: verificar se uma frase de 24 palavras corresponde ao endereco salvo. Nada e persistido. */
@Composable
fun VerifySeedScreen(vm: MainViewModel, onBack: () -> Unit) {
    SecureScreen()
    var text by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP, lifecycleOwner = ProcessLifecycleOwner.get()) {
        text = ""
        passphrase = ""
        vm.clearVerify()
    }
    BackHandler { text = ""; passphrase = ""; vm.clearVerify(); onBack() }

    val wordCount = Bip39.splitWords(text).size

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TerminalTitle("VERIFICAR FRASE")
        Spacer(Modifier.height(6.dp))
        Text(
            "Digite suas 24 palavras para confirmar que elas geram o endereco salvo. A frase e processada apenas em memoria, " +
                "com capturas de tela bloqueadas, e e apagada ao sair desta tela.",
            style = MaterialTheme.typography.bodySmall,
            color = MatrixColors.GreenDark
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.lowercase() },
            label = { Text("24 palavras separadas por espaco ($wordCount/24)") },
            minLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            colors = matrixTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Passphrase (se usou)") },
            singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = MatrixColors.GreenDark)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = matrixTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        when (val r = vm.verifyResult) {
            null -> {}
            is br.com.cavaleiros.mineradores.ui.VerifyResult.Match -> MatrixPanel(accent = MatrixColors.Green) {
                Text("CONFERE: a frase gera exatamente o endereco salvo neste aparelho.", color = MatrixColors.GreenBright, style = MaterialTheme.typography.bodyMedium)
            }
            is br.com.cavaleiros.mineradores.ui.VerifyResult.Mismatch -> MatrixPanel(accent = MatrixColors.Red) {
                Text("NAO CONFERE. Esta frase (com esta passphrase) gera outro endereco:", color = MatrixColors.Red, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text(r.derivedAddress, color = MatrixColors.Amber, style = MaterialTheme.typography.bodySmall)
            }
            is br.com.cavaleiros.mineradores.ui.VerifyResult.Invalid -> WarningBox(r.message, danger = true)
        }
        Spacer(Modifier.height(14.dp))
        if (vm.verifying) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MatrixColors.Green) }
        } else {
            MatrixButton("Verificar", onClick = { vm.verifyMnemonic(text, passphrase) }, enabled = wordCount >= 12, filled = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        MatrixButton("Limpar e voltar", onClick = { text = ""; passphrase = ""; vm.clearVerify(); onBack() }, modifier = Modifier.fillMaxWidth())
    }
}
