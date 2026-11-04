package br.com.cavaleiros.mineradores.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * Efeito "chuva digital" desenhado com o Canvas nativo (barato: um drawText por
 * caractere visivel, sem alocacoes por quadro). Desligue com [enabled] para
 * poupar CPU enquanto o motor de mineracao estiver trabalhando.
 */
@Composable
fun MatrixRain(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alpha: Float = 0.55f,
    frameDelayMs: Long = 95L
) {
    if (!enabled) return
    val density = LocalDensity.current
    val cellPx = with(density) { 15.dp.toPx() }
    val state = remember { RainState() }
    var frame by mutableIntStateOf(0)

    LaunchedEffect(enabled) {
        while (isActive) {
            delay(frameDelayMs)
            frame++
        }
    }

    val paint = remember {
        Paint().apply {
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            textSize = cellPx * 0.95f
        }
    }

    Canvas(modifier = modifier) {
        @Suppress("UNUSED_VARIABLE") val tick = frame
        val cols = (size.width / cellPx).toInt().coerceAtLeast(1)
        val rows = (size.height / cellPx).toInt() + 2
        state.ensure(cols, rows)
        state.step(rows)
        val canvas = drawContext.canvas.nativeCanvas
        for (c in 0 until cols) {
            val head = state.head[c]
            val len = state.length[c]
            for (k in 0 until len) {
                val row = head - k
                if (row < 0 || row >= rows) continue
                val ch = state.glyph(c, row)
                val fade = 1f - (k.toFloat() / len)
                val a = (alpha * fade * 255).toInt().coerceIn(0, 255)
                if (k == 0) {
                    paint.color = android.graphics.Color.argb((alpha * 255).toInt(), 0xB6, 0xFF, 0xC8)
                } else {
                    paint.color = android.graphics.Color.argb(a, 0x00, 0xFF, 0x41)
                }
                canvas.drawText(CHARS, ch, ch + 1, c * cellPx + 1f, (row + 1) * cellPx, paint)
            }
        }
    }
}

private const val CHARS = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789ABCDEFXYZ<>+-*/=:#%&"

private class RainState {
    var cols = 0
    var rows = 0
    var head = IntArray(0)
    var length = IntArray(0)
    var speed = IntArray(0)
    var glyphs = IntArray(0)
    private val rnd = Random(System.nanoTime())

    fun ensure(c: Int, r: Int) {
        if (c == cols && r == rows) return
        cols = c
        rows = r
        head = IntArray(c) { rnd.nextInt(-r, r) }
        length = IntArray(c) { rnd.nextInt(6, 22) }
        speed = IntArray(c) { rnd.nextInt(1, 3) }
        glyphs = IntArray(c * r) { rnd.nextInt(CHARS.length) }
    }

    fun step(r: Int) {
        for (c in 0 until cols) {
            head[c] += speed[c]
            if (head[c] - length[c] > r) {
                head[c] = rnd.nextInt(-r / 2, 0)
                length[c] = rnd.nextInt(6, 22)
                speed[c] = rnd.nextInt(1, 3)
            }
        }
        // troca alguns glifos por quadro para dar "vida" sem custo alto
        repeat((cols * 2).coerceAtLeast(4)) {
            val i = rnd.nextInt(glyphs.size.coerceAtLeast(1))
            if (i < glyphs.size) glyphs[i] = rnd.nextInt(CHARS.length)
        }
    }

    fun glyph(c: Int, row: Int): Int = glyphs[(c * rows + row).coerceIn(0, glyphs.size - 1)]
}

private val Int.dp: androidx.compose.ui.unit.Dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
