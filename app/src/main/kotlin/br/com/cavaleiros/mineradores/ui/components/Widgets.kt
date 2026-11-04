package br.com.cavaleiros.mineradores.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.cavaleiros.mineradores.R
import br.com.cavaleiros.mineradores.data.CpuLoad
import br.com.cavaleiros.mineradores.data.MinerSettings
import br.com.cavaleiros.mineradores.ui.theme.MatrixColors
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Locale
import kotlin.math.roundToInt

fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Contador de telas seguras ativas. Durante uma transicao de navegacao a tela
 * nova entra em composicao ANTES de a antiga ser descartada; sem contagem, o
 * onDispose da antiga limparia a flag que a nova acabou de ativar.
 */
private object SecureWindowCounter {
    private var count = 0

    fun acquire(window: android.view.Window?) {
        count++
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: android.view.Window?) {
        count = (count - 1).coerceAtLeast(0)
        if (count == 0) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * Impede capturas de tela e esconde o conteudo na troca de apps enquanto o
 * composable estiver na tela (FLAG_SECURE).
 */
@Composable
fun SecureScreen() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        SecureWindowCounter.acquire(window)
        onDispose { SecureWindowCounter.release(window) }
    }
}

@Composable
fun matrixTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MatrixColors.Green,
    unfocusedBorderColor = MatrixColors.GreenDim,
    disabledBorderColor = MatrixColors.GreenGhost,
    errorBorderColor = MatrixColors.Red,
    cursorColor = MatrixColors.Green,
    focusedTextColor = MatrixColors.GreenBright,
    unfocusedTextColor = MatrixColors.Green,
    disabledTextColor = MatrixColors.GreenDark,
    focusedLabelColor = MatrixColors.Green,
    unfocusedLabelColor = MatrixColors.GreenDark,
    errorLabelColor = MatrixColors.Red,
    focusedPlaceholderColor = MatrixColors.GreenDim,
    unfocusedPlaceholderColor = MatrixColors.GreenDim,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedSupportingTextColor = MatrixColors.GreenDark,
    unfocusedSupportingTextColor = MatrixColors.GreenDark,
    errorSupportingTextColor = MatrixColors.Red
)

/** Painel com borda fina verde, estilo terminal. */
@Composable
fun MatrixPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = MatrixColors.GreenDim,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MatrixColors.Panel.copy(alpha = 0.88f), RoundedCornerShape(4.dp))
            .border(1.dp, accent, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        if (title != null) {
            Text(
                text = "// $title",
                style = MaterialTheme.typography.labelLarge,
                color = MatrixColors.GreenDark
            )
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
fun MatrixButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    danger: Boolean = false
) {
    val color = if (danger) MatrixColors.Red else MatrixColors.Green
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = MatrixColors.Black,
                disabledContainerColor = MatrixColors.GreenDim,
                disabledContentColor = MatrixColors.GreenDark
            )
        ) {
            Text(text.uppercase(), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) color else MatrixColors.GreenDim),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = color,
                disabledContentColor = MatrixColors.GreenDim
            )
        ) {
            Text(text.uppercase(), letterSpacing = 1.sp)
        }
    }
}

/** Titulo estilo prompt com cursor piscando. */
@Composable
fun TerminalTitle(text: String, modifier: Modifier = Modifier, small: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink"
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "> $text",
            style = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            color = MatrixColors.Green,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = "_",
            style = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            color = MatrixColors.Green,
            modifier = Modifier.alpha(blink)
        )
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier = modifier
            .background(MatrixColors.PanelAlt.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            .border(1.dp, if (highlight) MatrixColors.Green else MatrixColors.GreenGhost, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDark)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = if (highlight) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = if (highlight) MatrixColors.GreenBright else MatrixColors.Green,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun InfoLine(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MatrixColors.Green) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MatrixColors.GreenDark)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
fun WarningBox(text: String, modifier: Modifier = Modifier, danger: Boolean = false) {
    val color = if (danger) MatrixColors.Red else MatrixColors.Amber
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        Text("!", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

/** Log em estilo terminal com rolagem automatica para a ultima linha. */
@Composable
fun TerminalLog(lines: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MatrixColors.Black, RoundedCornerShape(4.dp))
            .border(1.dp, MatrixColors.GreenDim, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        if (lines.isEmpty()) {
            Text("> aguardando eventos...", style = MaterialTheme.typography.bodySmall, color = MatrixColors.GreenDim)
        } else {
            LazyColumn(state = listState) {
                itemsIndexed(lines) { _, line ->
                    val color = when {
                        line.contains("BLOCO ENCONTRADO") -> MatrixColors.GreenBright
                        line.contains("ACEITO") -> MatrixColors.Green
                        line.contains("REJEITADO") || line.contains("falha") || line.contains("erro") || line.contains("recusada") -> MatrixColors.Red
                        line.contains("pausado") || line.contains("desconectado") -> MatrixColors.Amber
                        else -> MatrixColors.GreenDark
                    }
                    Text(line, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp), color = color)
                }
            }
        }
    }
}

/** QR code verde sobre preto, renderizado com ZXing (sem bitmap intermediario). */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, sizeDp: Int = 200) {
    val matrix = remember(content) {
        try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: Exception) {
            null
        }
    }
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .background(MatrixColors.Black, RoundedCornerShape(4.dp))
            .border(1.dp, MatrixColors.GreenDim, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        if (matrix != null) {
            Canvas(modifier = Modifier.size((sizeDp - 16).dp)) {
                val w = matrix.width
                val h = matrix.height
                val cell = minOf(size.width / w, size.height / h)
                val offX = (size.width - cell * w) / 2f
                val offY = (size.height - cell * h) / 2f
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        if (matrix.get(x, y)) {
                            drawRect(
                                color = MatrixColors.Green,
                                topLeft = Offset(offX + x * cell, offY + y * cell),
                                size = Size(cell + 0.5f, cell + 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun shortenAddress(address: String, head: Int = 10, tail: Int = 8): String =
    if (address.length <= head + tail + 3) address else address.take(head) + "..." + address.takeLast(tail)

fun formatUptime(startedAt: Long): String {
    if (startedAt <= 0) return "--"
    var s = (System.currentTimeMillis() - startedAt) / 1000
    val h = s / 3600; s %= 3600
    val m = s / 60; s %= 60
    return if (h > 0) String.format("%dh %02dm %02ds", h, m, s) else String.format("%02dm %02ds", m, s)
}

/** Humaniza uma duracao em segundos (para o "tempo esperado ate um bloco"). */
fun humanizeDuration(seconds: Double): String {
    if (!seconds.isFinite() || seconds <= 0) return "--"
    val minute = 60.0; val hour = 3600.0; val day = 86400.0; val year = 365.25 * day
    return when {
        seconds < minute -> String.format("%.0f s", seconds)
        seconds < hour -> String.format("%.0f min", seconds / minute)
        seconds < day -> String.format("%.1f h", seconds / hour)
        seconds < year -> String.format("%.0f dias", seconds / day)
        seconds < 1e3 * year -> String.format("%.0f anos", seconds / year)
        seconds < 1e6 * year -> String.format("%.0f mil anos", seconds / (1e3 * year))
        seconds < 1e9 * year -> String.format("%.1f milhoes de anos", seconds / (1e6 * year))
        seconds < 1e12 * year -> String.format("%.1f bilhoes de anos", seconds / (1e9 * year))
        else -> String.format("%.1f trilhoes de anos", seconds / (1e12 * year))
    }
}

@Composable
fun MatrixSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = MatrixColors.Green,
            activeTrackColor = MatrixColors.Green,
            inactiveTrackColor = MatrixColors.GreenDim,
            activeTickColor = MatrixColors.Black,
            inactiveTickColor = MatrixColors.GreenDark
        )
    )
}

/**
 * Barra de potencia: quanto da capacidade total do aparelho (0..100%) vai para a mineracao.
 * Mostra a barra segmentada, o controle deslizante (passos de 5%) e o par threads/intensidade resultante.
 */
@Composable
fun PowerBar(power: Int, running: Boolean, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cores = MinerSettings.maxThreads()
    val load = CpuLoad.fromPower(power, cores)
    var local by remember(power) { mutableIntStateOf(power) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("POTENCIA DO CELULAR", style = MaterialTheme.typography.labelSmall, color = MatrixColors.GreenDark)
            Text(
                "$local%",
                style = MaterialTheme.typography.titleLarge,
                color = if (local >= 90) MatrixColors.Amber else MatrixColors.GreenBright
            )
        }
        Spacer(Modifier.height(6.dp))
        val segments = 25
        val lit = (local * segments + 50) / 100
        Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until segments) {
                val color = when {
                    i < lit && i >= segments * 9 / 10 -> MatrixColors.Amber
                    i < lit -> MatrixColors.Green
                    else -> MatrixColors.GreenDim
                }
                Box(Modifier.weight(1f).fillMaxHeight().background(color, RoundedCornerShape(1.dp)))
            }
        }
        MatrixSlider(local.toFloat(), 0f..100f, 19) { v ->
            val p = ((v / 5f).roundToInt() * 5).coerceIn(0, 100)
            if (p != local) {
                local = p
                onChange(p)
            }
        }
        val equiv = String.format(Locale.US, "%.1f", local / 100.0 * cores)
        Text(
            when {
                load.threads == 0 -> "0% = em espera: continua conectado ao pool, mas nao calcula hashes."
                else -> "Equivale a $equiv de $cores nucleos (${load.label})." + if (running) " Aplicado na hora, sem reconectar." else ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = MatrixColors.GreenDim
        )
    }
}

const val INSTAGRAM_HANDLE = "cavaleiro.ai"

/** Convite para seguir o perfil no Instagram; abre o app do Instagram ou o navegador. */
@Composable
fun InstagramLink(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val open = {
        // 1) app do Instagram, 2) navegador, 3) aviso com o @ (aparelhos sem navegador)
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("instagram://user?username=$INSTAGRAM_HANDLE")).setPackage("com.instagram.android"),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$INSTAGRAM_HANDLE/"))
        )
        val opened = candidates.any { intent ->
            try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
        if (!opened) {
            android.widget.Toast.makeText(context, "Nenhum navegador encontrado. Procure @$INSTAGRAM_HANDLE no Instagram.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    Row(
        modifier.clickable(onClick = open).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text("siga ", style = MaterialTheme.typography.bodySmall, color = MatrixColors.GreenDark)
        Text("@$INSTAGRAM_HANDLE", style = MaterialTheme.typography.bodyMedium, color = MatrixColors.GreenBright, fontWeight = FontWeight.Bold)
        Text(" no instagram", style = MaterialTheme.typography.bodySmall, color = MatrixColors.GreenDark)
    }
}

/** Logo do app: escudo vetorial detalhado com um brilho verde pulsando atras. */
@Composable
fun ShieldLogo(modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val transition = rememberInfiniteTransition(label = "logo")
    val glow by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    Box(
        modifier
            .size(size)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(listOf(MatrixColors.Green.copy(alpha = glow), Color.Transparent)),
                    radius = this.size.minDimension * 0.6f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_shield_logo),
            contentDescription = "Kabayero Miner",
            modifier = Modifier.fillMaxSize()
        )
    }
}
