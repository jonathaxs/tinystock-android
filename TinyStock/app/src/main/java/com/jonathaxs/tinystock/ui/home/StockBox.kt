package com.jonathaxs.tinystock.ui.home

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

/** Cores de uma barra: lado esquerdo (sombra), lado direito e topo (mais claro). */
@Immutable
data class BarPalette(val left: Color, val right: Color, val top: Color)

/** Uma barra que sai do topo da caixa. */
@Immutable
data class StockBoxBar(val value: Int, val palette: BarPalette)

/** Cores da caixa e das barras, as mesmas do icone e do iOS. */
object StockBoxColors {
    val leftFace = listOf(Color(0xFFEF7645), Color(0xFFD2532B))
    val rightFace = listOf(Color(0xFFFF9B66), Color(0xFFF07440))
    val innerLeft = listOf(Color(0xFFC2602F), Color(0xFF8A3A18))
    val innerRight = listOf(Color(0xFFB5582A), Color(0xFF7E3414))

    /** Borda cortada da caixa, mais clara, como papelao cortado. */
    val cutEdge = Color(0xFFFFB48C)
    val backCutEdge = Color(0xFFE88A5C)

    /** Cor de cada lado na legenda do balao. */
    val leftLegend = leftFace[0]
    val rightLegend = rightFace[0]

    val teal = BarPalette(Color(0xFF36A7A4), Color(0xFF51BEB9), Color(0xFF7DCBC7))
    val deepTeal = BarPalette(Color(0xFF116B6B), Color(0xFF198E8B), Color(0xFF4DA8A5))

    /** Grafite no claro e creme no escuro, como a barra do meio do icone. */
    fun graphite(darkTheme: Boolean) = if (darkTheme) {
        BarPalette(Color(0xFFE7DED1), Color(0xFFF7F4EF), Color(0xFFFDFBFA))
    } else {
        BarPalette(Color(0xFF292D3A), Color(0xFF424956), Color(0xFF666E7F))
    }
}

/** Tamanho do desenho; a caixa e uma ilustracao com medidas fixas. */
private const val BOX_WIDTH = 200f
private const val BOX_HEIGHT = 252f

// Geometria em dp, igual a do iOS: perspectiva isometrica 2:1.
private const val SLOPE = 0.5f
private const val HALF_WIDTH = 80f
private const val FACE_HEIGHT = 74f
private const val MIN_BAR_RISE = 30f
private const val MAX_BAR_RISE = 86f
private const val BAR_SIDE = 0.2f

/** Posicao de cada barra no fundo da caixa (p para a direita, q para a esquerda). */
private val barSlots = listOf(0.12f to 0.58f, 0.40f to 0.40f, 0.58f to 0.12f)

/**
 * Caixa sem tampa nas cores do icone, em perspectiva isometrica: um numero em cada lado e tres barras
 * saindo do topo, com altura proporcional ao valor. Porte do `StockBoxView` do iOS. Decorativa: quem usa
 * da a descricao para o TalkBack.
 */
@Composable
fun StockBox(
    leftValue: Int,
    leftLabel: String,
    rightValue: Int,
    rightLabel: String,
    bars: List<StockBoxBar>,
    growth: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onBackground
    val density = LocalDensity.current
    // Texto com tamanho fixo, como no iOS: a ilustracao nao cresce com a fonte do sistema.
    val styles = remember(density) { BoxTextStyles(density) }
    Canvas(modifier.size(BOX_WIDTH.dp, BOX_HEIGHT.dp)) {
        drawBox(leftValue, leftLabel, rightValue, rightLabel, bars, growth, measurer, styles, labelColor)
    }
}

private class BoxTextStyles(density: Density) {
    val number = TextStyle(color = Color.White, fontSize = with(density) { 28.dp.toSp() }, fontWeight = FontWeight.Bold)
    val caption = TextStyle(
        color = Color.White.copy(alpha = 0.92f),
        fontSize = with(density) { 11.dp.toSp() },
        fontWeight = FontWeight.SemiBold,
    )
    val bar = TextStyle(fontSize = with(density) { 15.dp.toSp() }, fontWeight = FontWeight.Bold)
}

private fun DrawScope.drawBox(
    leftValue: Int,
    leftLabel: String,
    rightValue: Int,
    rightLabel: String,
    bars: List<StockBoxBar>,
    growth: Float,
    measurer: TextMeasurer,
    styles: BoxTextStyles,
    labelColor: Color,
) {
    val u = density
    fun pt(x: Float, y: Float) = Offset(x * u, y * u)

    val cx = BOX_WIDTH / 2
    // Margem embaixo para a sombra desfocada nao ser cortada.
    val bottom = BOX_HEIGHT - 16
    val w = HALF_WIDTH
    val k = SLOPE
    val h = FACE_HEIGHT

    // Cantos da caixa: frente, esquerda, direita e fundo; Bottom embaixo, Top na borda cortada.
    val frontBottom = pt(cx, bottom)
    val leftBottom = pt(cx - w, bottom - k * w)
    val rightBottom = pt(cx + w, bottom - k * w)
    val backBottom = pt(cx, bottom - 2 * k * w)
    val frontTop = pt(cx, bottom - h)
    val leftTop = pt(cx - w, bottom - k * w - h)
    val rightTop = pt(cx + w, bottom - k * w - h)
    val backTop = pt(cx, bottom - 2 * k * w - h)
    val down = Offset(0f, h * u)

    // Sombra no chao, para a caixa nao flutuar.
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = Color.Black.copy(alpha = 0.18f).toArgb()
            maskFilter = BlurMaskFilter(6 * u, BlurMaskFilter.Blur.NORMAL)
        }
        val shadow = polygon(listOf(leftBottom, frontBottom, rightBottom, backBottom).map { it + Offset(0f, 4 * u) })
        canvas.nativeCanvas.drawPath(shadow.asAndroidPath(), paint)
    }

    // Abertura: paredes internas de tras, mais escuras para o fundo.
    val opening = polygon(listOf(leftTop, frontTop, rightTop, backTop))
    val innerDepth = Offset(backTop.x, frontTop.y)
    drawPath(opening, Brush.linearGradient(StockBoxColors.innerLeft, backTop, innerDepth))
    val innerRight = polygon(listOf(backTop, rightTop, rightTop + down, backTop + down))
    clipPathCompat(opening) {
        drawPath(innerRight, Brush.linearGradient(StockBoxColors.innerRight, backTop, innerDepth))
    }

    // Borda cortada das paredes de tras (fica atras das barras).
    drawPath(
        polyline(listOf(leftTop, backTop, rightTop)),
        StockBoxColors.backCutEdge,
        style = Stroke(width = 2 * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // Barras, de tras para a frente.
    val maxValue = maxOf(bars.maxOfOrNull { it.value } ?: 0, 1)
    val order = barSlots.indices.sortedByDescending { barSlots[it].first + barSlots[it].second }
    val labelSpots = mutableListOf<Pair<Int, Offset>>()
    for (index in order) {
        if (index >= bars.size) continue
        val bar = bars[index]
        val rise = MIN_BAR_RISE + (MAX_BAR_RISE - MIN_BAR_RISE) * bar.value / maxValue
        val height = (h + rise) * growth
        labelSpots += index to drawBar(barSlots[index], height, bar.palette, cx, bottom, u)
    }

    // Lados da frente, por cima das barras.
    drawPath(
        polygon(listOf(leftBottom, frontBottom, frontTop, leftTop)),
        Brush.linearGradient(StockBoxColors.leftFace, midpoint(leftTop, frontTop), midpoint(leftBottom, frontBottom)),
    )
    drawPath(
        polygon(listOf(frontBottom, rightBottom, rightTop, frontTop)),
        Brush.linearGradient(StockBoxColors.rightFace, midpoint(frontTop, rightTop), midpoint(frontBottom, rightBottom)),
    )
    // Borda cortada da frente.
    drawPath(
        polyline(listOf(leftTop, frontTop, rightTop)),
        StockBoxColors.cutEdge,
        style = Stroke(width = 2.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // Numeros pintados nos lados, inclinados junto com cada lado.
    val leftCenter = pt(cx - w / 2, bottom - k * w / 2 - h / 2)
    val rightCenter = pt(cx + w / 2, bottom - k * w / 2 - h / 2)
    drawFaceText(leftValue, leftLabel, leftCenter, k, measurer, styles, u)
    drawFaceText(rightValue, rightLabel, rightCenter, -k, measurer, styles, u)

    // Valor em cima de cada barra.
    if (growth <= 0.6f) return
    for ((index, spot) in labelSpots) {
        val layout = measurer.measure(formatNumber(bars[index].value), styles.bar.copy(color = labelColor))
        // Acima do vertice de tras do topo, para nao encostar na barra.
        val baseY = spot.y - (BAR_SIDE * SLOPE * HALF_WIDTH + 4) * u
        drawText(layout, topLeft = Offset(spot.x - layout.size.width / 2f, baseY - layout.size.height))
    }
}

/** Prisma de base quadrada no fundo da caixa; devolve o centro do topo. */
private fun DrawScope.drawBar(
    slot: Pair<Float, Float>,
    height: Float,
    palette: BarPalette,
    cx: Float,
    bottom: Float,
    u: Float,
): Offset {
    fun point(p: Float, q: Float, z: Float) = Offset(
        (cx + (p - q) * HALF_WIDTH) * u,
        (bottom - (p + q) * SLOPE * HALF_WIDTH - z) * u,
    )
    val (p0, q0) = slot
    val p1 = p0 + BAR_SIDE
    val q1 = q0 + BAR_SIDE
    val top = point(p0, q0, height)
    val base = point(p0, q0, 0f)
    // Lados escurecem para baixo, como os lados da caixa.
    drawPath(
        polygon(listOf(point(p0, q0, 0f), point(p0, q1, 0f), point(p0, q1, height), point(p0, q0, height))),
        Brush.linearGradient(listOf(palette.left, lerp(palette.left, Color.Black, 0.25f)), top, base),
    )
    drawPath(
        polygon(listOf(point(p0, q0, 0f), point(p1, q0, 0f), point(p1, q0, height), point(p0, q0, height))),
        Brush.linearGradient(listOf(palette.right, lerp(palette.right, Color.Black, 0.25f)), top, base),
    )
    drawPath(
        polygon(listOf(point(p0, q0, height), point(p1, q0, height), point(p1, q1, height), point(p0, q1, height))),
        palette.top,
    )
    return point(p0 + BAR_SIDE / 2, q0 + BAR_SIDE / 2, height)
}

/** Numero grande e rotulo pequeno, encolhendo para caber na largura do lado. */
private fun DrawScope.drawFaceText(
    value: Int,
    label: String,
    center: Offset,
    shear: Float,
    measurer: TextMeasurer,
    styles: BoxTextStyles,
    u: Float,
) {
    val number = measurer.measure(formatNumber(value), styles.number)
    val caption = measurer.measure(label, styles.caption)
    val maxWidth = (HALF_WIDTH - 14) * u
    val widest = maxOf(number.size.width, caption.size.width, 1).toFloat()
    val scale = minOf(1f, maxWidth / widest)
    // Cisalhamento vertical (y' = y + shear * x), como o CGAffineTransform(b: shear) do iOS.
    val skew = Matrix().apply { values[Matrix.SkewY] = shear }
    withTransform({
        translate(center.x, center.y)
        transform(skew)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawText(number, topLeft = Offset(-number.size.width / 2f, -6 * u - number.size.height / 2f))
        val captionCenterY = number.size.height / 2f + 2 * u
        drawText(caption, topLeft = Offset(-caption.size.width / 2f, captionCenterY - caption.size.height / 2f))
    }
}

private inline fun DrawScope.clipPathCompat(path: Path, block: DrawScope.() -> Unit) {
    withTransform({ clipPath(path) }, block)
}

private fun polygon(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (point in points.drop(1)) lineTo(point.x, point.y)
    close()
}

private fun polyline(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (point in points.drop(1)) lineTo(point.x, point.y)
}

private fun midpoint(a: Offset, b: Offset) = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)

private fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance().format(value)
