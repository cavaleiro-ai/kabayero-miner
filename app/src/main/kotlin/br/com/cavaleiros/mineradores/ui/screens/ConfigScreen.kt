package br.com.cavaleiros.mineradores.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.cavaleiros.mineradores.data.MinerSettings
import br.com.cavaleiros.mineradores.data.MiningMode
import br.com.cavaleiros.mineradores.data.PoolPreset
import br.com.cavaleiros.mineradores.data.PoolPresets
import br.com.cavaleiros.mineradores.ui.MainViewModel
import br.com.cavaleiros.mineradores.ui.components.InstagramLink
import br.com.cavaleiros.mineradores.ui.components.MatrixButton
import br.com.cavaleiros.mineradores.ui.components.MatrixSlider
import br.com.cavaleiros.mineradores.ui.components.PowerBar
import br.com.cavaleiros.mineradores.ui.components.MatrixPanel
import br.com.cavaleiros.mineradores.ui.components.TerminalTitle
import br.com.cavaleiros.mineradores.ui.components.WarningBox
import br.com.cavaleiros.mineradores.ui.components.matrixTextFieldColors
import br.com.cavaleiros.mineradores.ui.theme.MatrixColors

@Composable
fun ConfigScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.miningStats.collectAsStateWithLifecycle()
    val update: ((MinerSettings) -> MinerSettings) -> Unit = { vm.updateSettings(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        TerminalTitle("CONFIGURAR MINERACAO")
        Spacer(Modifier.height(10.dp))
        if (stats.running) {
            WarningBox("A mineracao esta em andamento. Alteracoes passam a valer na proxima vez que voce iniciar.")
            Spacer(Modifier.height(12.dp))
        }

        // ------------------------------------------------------------ modo
        MatrixPanel(title = "modo") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiningMode.values().forEach { mode ->
                    val selected = settings.mode == mode
                    Column(
                        Modifier
                            .weight(1f)
                            .background(if (selected) MatrixColors.GreenDim else MatrixColors.Black, RoundedCornerShape(4.dp))
                            .border(1.dp, if (selected) MatrixColors.Green else MatrixColors.GreenDim, RoundedCornerShape(4.dp))
                            .clickable {
                                update {
                                    val firstPreset = if (mode == MiningMode.SOLO) PoolPresets.SOLO.first() else PoolPresets.POOL.first()
                                    val keepCustom = it.presetId == PoolPresets.CUSTOM_ID
                                    it.copy(mode = mode, presetId = if (keepCustom) it.presetId else firstPreset.id)
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Text(mode.label.uppercase(), fontWeight = FontWeight.Bold, color = if (selected) MatrixColors.GreenBright else MatrixColors.GreenDark)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(settings.mode.description, style = MaterialTheme.typography.bodySmall, color = MatrixColors.GreenDark)
        }
        Spacer(Modifier.height(12.dp))

        // ------------------------------------------------------------ servidor
        MatrixPanel(title = "servidor stratum") {
            val presets = if (settings.mode == MiningMode.SOLO) PoolPresets.SOLO else PoolPresets.POOL
            presets.forEach { preset -> PresetRow(preset, settings.presetId == preset.id) { update { it.copy(presetId = preset.id) } } }
            Row(
                Modifier.fillMaxWidth().clickable { update { it.copy(presetId = PoolPresets.CUSTOM_ID) } }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.presetId == PoolPresets.CUSTOM_ID,
                    onClick = { update { it.copy(presetId = PoolPresets.CUSTOM_ID) } },
                    colors = RadioButtonDefaults.colors(selectedColor = MatrixColors.Green, unselectedColor = MatrixColors.GreenDark)
                )
                Column {
                    Text("Personalizado", color = MatrixColors.Green, style = MaterialTheme.typography.bodyMedium)
                    Text("Qualquer servidor Stratum V1 (stratum+tcp ou stratum+ssl)", color = MatrixColors.GreenDark, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (settings.presetId == PoolPresets.CUSTOM_ID) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = settings.customLabel,
                    onValueChange = { v -> update { it.copy(customLabel = v.take(40)) } },
                    label = { Text("Nome") }, singleLine = true, colors = matrixTextFieldColors(), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settings.customHost,
                        onValueChange = { v -> update { it.copy(customHost = v.trim().removePrefix("stratum+tcp://").removePrefix("stratum+ssl://").removePrefix("stratum://")) } },
                        label = { Text("Host") }, singleLine = true, colors = matrixTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = settings.customPort.toString(),
                        onValueChange = { v -> v.filter { c -> c.isDigit() }.take(5).toIntOrNull()?.let { p -> update { it.copy(customPort = p) } } },
                        label = { Text("Porta") }, singleLine = true, colors = matrixTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                SwitchRow("Usar TLS (stratum+ssl)", settings.customTls) { v -> update { it.copy(customTls = v) } }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.workerName,
                onValueChange = { v -> update { it.copy(workerName = v.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(32)) } },
                label = { Text("Nome do worker (usuario = endereco.worker)") },
                singleLine = true, colors = matrixTextFieldColors(), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false)
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = settings.password,
                onValueChange = { v -> update { it.copy(password = v.take(64)) } },
                label = { Text("Senha do worker (normalmente \"x\")") },
                singleLine = true, colors = matrixTextFieldColors(), modifier = Modifier.fillMaxWidth()
            )
            SwitchRow("Sugerir dificuldade baixa ao pool (feedback de shares mais rapido)", settings.suggestDifficulty) { v -> update { it.copy(suggestDifficulty = v) } }
            Text(
                "Dica: se o pool ignorar a sugestao e mantiver dificuldade alta, muitos aceitam \"d=1\" ou \"diff=1\" no campo de senha.",
                style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDim
            )
        }
        Spacer(Modifier.height(12.dp))

        // ------------------------------------------------------------ desempenho
        MatrixPanel(title = "potencia") {
            PowerBar(settings.power, running = stats.running, onChange = { p -> update { it.copy(power = p) } })
            Text(
                "Menos potencia = menos calor e consumo. Use 100% apenas com o aparelho carregando e bem ventilado. A mesma barra aparece no painel.",
                style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDim
            )
        }
        Spacer(Modifier.height(12.dp))

        MatrixPanel(title = "interface") {
            SwitchRow("Efeito chuva digital (desligado automaticamente ao minerar)", settings.matrixRain) { v -> update { it.copy(matrixRain = v) } }
        }
        Spacer(Modifier.height(12.dp))

        MatrixPanel(title = "comunidade") {
            Text("Novidades do app, dicas de mineracao e suporte:", color = MatrixColors.Green, style = MaterialTheme.typography.bodySmall)
            InstagramLink(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PresetRow(preset: PoolPreset, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = MatrixColors.Green, unselectedColor = MatrixColors.GreenDark)
        )
        Column(Modifier.padding(top = 10.dp)) {
            Text("${preset.name}  :${preset.port}${if (preset.tls) " TLS" else ""}", color = if (selected) MatrixColors.GreenBright else MatrixColors.Green, style = MaterialTheme.typography.bodyMedium)
            Text(preset.description, color = MatrixColors.GreenDark, style = MaterialTheme.typography.labelSmall)
            if (preset.minPayoutNote.isNotEmpty()) Text(preset.minPayoutNote, color = MatrixColors.GreenDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MatrixColors.Green, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MatrixColors.Black,
                checkedTrackColor = MatrixColors.Green,
                uncheckedThumbColor = MatrixColors.GreenDark,
                uncheckedTrackColor = MatrixColors.Black,
                uncheckedBorderColor = MatrixColors.GreenDim,
                checkedBorderColor = MatrixColors.Green
            )
        )
    }
}
