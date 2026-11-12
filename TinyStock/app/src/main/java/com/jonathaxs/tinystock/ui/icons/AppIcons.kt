package com.jonathaxs.tinystock.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icones que faltam no material-icons-core (o pacote estendido e grande demais). Os SF Symbols do iOS
 * viram estes vetores, desenhados na grade de 24 do Material.
 */
object AppIcons {

    /** Equivale ao "minus" do iOS. */
    val Minus: ImageVector by lazy {
        icon("Minus") {
            moveTo(19f, 13f)
            horizontalLineTo(5f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(14f)
            close()
        }
    }

    /** Equivale ao "chart.bar" do iOS. */
    val BarChart: ImageVector by lazy {
        icon("BarChart") {
            moveTo(5f, 9.2f)
            horizontalLineToRelative(3f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
            moveTo(10.6f, 5f)
            horizontalLineToRelative(2.8f)
            verticalLineToRelative(14f)
            horizontalLineToRelative(-2.8f)
            close()
            moveTo(16.2f, 13f)
            horizontalLineTo(19f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(-2.8f)
            close()
        }
    }

    /** Equivale ao "shippingbox" do iOS: caixa em perspectiva, so o contorno. */
    val Box: ImageVector by lazy {
        strokeIcon("Box") {
            moveTo(12f, 2.8f)
            lineTo(20.2f, 7.4f)
            verticalLineTo(16.6f)
            lineTo(12f, 21.2f)
            lineTo(3.8f, 16.6f)
            verticalLineTo(7.4f)
            close()
            moveTo(3.8f, 7.4f)
            lineTo(12f, 12f)
            lineTo(20.2f, 7.4f)
            moveTo(12f, 12f)
            verticalLineTo(21.2f)
            moveTo(7.9f, 5.1f)
            lineTo(16.1f, 9.7f)
        }
    }

    /** Equivale ao "square.stack.3d.up" do iOS: camadas empilhadas (variacoes). */
    val Layers: ImageVector by lazy {
        strokeIcon("Layers") {
            moveTo(12f, 3f)
            lineTo(21f, 8f)
            lineTo(12f, 13f)
            lineTo(3f, 8f)
            close()
            moveTo(3f, 12f)
            lineTo(12f, 17f)
            lineTo(21f, 12f)
            moveTo(3f, 16f)
            lineTo(12f, 21f)
            lineTo(21f, 16f)
        }
    }

    /** Equivale ao "storefront" do iOS: toldo e porta. */
    val Storefront: ImageVector by lazy {
        strokeIcon("Storefront") {
            moveTo(4.5f, 4f)
            horizontalLineTo(19.5f)
            lineTo(21f, 9f)
            horizontalLineTo(3f)
            close()
            moveTo(5f, 9f)
            verticalLineTo(20f)
            horizontalLineTo(19f)
            verticalLineTo(9f)
            moveTo(10f, 20f)
            verticalLineTo(14.5f)
            horizontalLineTo(14f)
            verticalLineTo(20f)
        }
    }

    /** Equivale ao "square.grid.2x2" do iOS: quatro quadrados com cantos arredondados. */
    val Grid: ImageVector by lazy {
        strokeIcon("Grid") {
            for ((x, y) in listOf(4f to 4f, 13.5f to 4f, 4f to 13.5f, 13.5f to 13.5f)) {
                moveTo(x + 1.5f, y)
                horizontalLineToRelative(3.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
                verticalLineToRelative(3.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
                horizontalLineToRelative(-3.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
                verticalLineToRelative(-3.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
                close()
            }
        }
    }

    /** Equivale ao "list.bullet" do iOS: tres linhas com um ponto na frente. */
    val ListBullet: ImageVector by lazy {
        strokeIcon("ListBullet") {
            for (y in listOf(6f, 12f, 18f)) {
                moveTo(4.5f, y)
                lineTo(4.6f, y)
                moveTo(9f, y)
                horizontalLineTo(20f)
            }
        }
    }

    /** Equivale ao "globe" do iOS: circulo com meridiano e paralelos. */
    val Globe: ImageVector by lazy {
        strokeIcon("Globe") {
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, true, true, 18f, 0f)
            arcToRelative(9f, 9f, 0f, true, true, -18f, 0f)
            close()
            moveTo(12f, 3f)
            arcToRelative(4f, 9f, 0f, false, true, 0f, 18f)
            arcToRelative(4f, 9f, 0f, false, true, 0f, -18f)
            close()
            moveTo(3f, 12f)
            horizontalLineTo(21f)
            moveTo(4.6f, 7.5f)
            horizontalLineTo(19.4f)
            moveTo(4.6f, 16.5f)
            horizontalLineTo(19.4f)
        }
    }

    /** Equivale ao "arrow.up.forward.app" do iOS: abre fora do app. */
    val OpenExternal: ImageVector by lazy {
        strokeIcon("OpenExternal") {
            moveTo(11f, 4.5f)
            horizontalLineTo(6f)
            arcToRelative(1.5f, 1.5f, 0f, false, false, -1.5f, 1.5f)
            verticalLineTo(18f)
            arcToRelative(1.5f, 1.5f, 0f, false, false, 1.5f, 1.5f)
            horizontalLineTo(18f)
            arcToRelative(1.5f, 1.5f, 0f, false, false, 1.5f, -1.5f)
            verticalLineTo(13f)
            moveTo(14f, 4.5f)
            horizontalLineTo(19.5f)
            verticalLineTo(10f)
            moveTo(19.5f, 4.5f)
            lineTo(11.5f, 12.5f)
        }
    }

    /** Equivale ao "externaldrive" do iOS: disco com luz e fenda. */
    val Drive: ImageVector by lazy {
        strokeIcon("Drive") {
            moveTo(5f, 7f)
            horizontalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(15f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(9f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
            moveTo(16.5f, 12f)
            lineTo(16.6f, 12f)
            moveTo(6.5f, 12f)
            horizontalLineTo(11f)
        }
    }

    /** Equivale ao "paintpalette" do iOS: paleta com o encaixe do dedo e tres tintas. */
    val Palette: ImageVector by lazy {
        strokeIcon("Palette") {
            moveTo(12f, 21f)
            arcTo(9f, 9f, 0f, true, true, 21f, 12f)
            quadTo(21f, 15f, 17.5f, 15f)
            quadTo(14.5f, 15f, 15f, 17.5f)
            quadTo(15.5f, 21f, 12f, 21f)
            close()
            for ((x, y) in listOf(7.8f to 10.5f, 10.5f to 7.2f, 14.8f to 7.6f, 8.2f to 15f)) {
                moveTo(x, y)
                lineTo(x + 0.1f, y)
            }
        }
    }

    /** Equivale ao "hammer.fill" do iOS: cabo inclinado e a cabeca atravessada, preenchidos. */
    val Hammer: ImageVector by lazy {
        icon("Hammer") {
            moveTo(19.8f, 12f)
            lineTo(17f, 14.8f)
            lineTo(9.2f, 7f)
            lineTo(12f, 4.2f)
            close()
            moveTo(3.2f, 19.2f)
            lineTo(12f, 10.4f)
            lineTo(13.6f, 12f)
            lineTo(4.8f, 20.8f)
            close()
        }
    }

    /** Equivale ao "chart.line.uptrend.xyaxis" do iOS. */
    val TrendUp: ImageVector by lazy {
        strokeIcon("TrendUp") {
            moveTo(3.5f, 17.5f)
            lineTo(9.5f, 11.5f)
            lineTo(13.5f, 15f)
            lineTo(20.5f, 7.5f)
            moveTo(15f, 7.5f)
            horizontalLineTo(20.5f)
            verticalLineTo(13f)
        }
    }

    /** Equivale ao "percent" do iOS. */
    val Percent: ImageVector by lazy {
        strokeIcon("Percent") {
            moveTo(18.5f, 5.5f)
            lineTo(5.5f, 18.5f)
            circle(7.5f, 7.5f, 2.3f)
            circle(16.5f, 16.5f, 2.3f)
        }
    }

    /** Equivale ao "brazilianrealsign" do iOS: nota de dinheiro com a moeda no meio. */
    val Money: ImageVector by lazy {
        strokeIcon("Money") {
            moveTo(5f, 6.5f)
            horizontalLineTo(19f)
            quadTo(21f, 6.5f, 21f, 8.5f)
            verticalLineTo(15.5f)
            quadTo(21f, 17.5f, 19f, 17.5f)
            horizontalLineTo(5f)
            quadTo(3f, 17.5f, 3f, 15.5f)
            verticalLineTo(8.5f)
            quadTo(3f, 6.5f, 5f, 6.5f)
            close()
            circle(12f, 12f, 2.6f)
        }
    }

    /** Equivale ao "xmark.circle.fill" do iOS. */
    val CancelCircle: ImageVector by lazy {
        strokeIcon("CancelCircle") {
            circle(12f, 12f, 8.8f)
            moveTo(9f, 9f)
            lineTo(15f, 15f)
            moveTo(15f, 9f)
            lineTo(9f, 15f)
        }
    }

    /** Equivale ao "chevron.up.chevron.down" do iOS. */
    val UpDown: ImageVector by lazy {
        strokeIcon("UpDown") {
            moveTo(8f, 9.5f)
            lineTo(12f, 5.5f)
            lineTo(16f, 9.5f)
            moveTo(8f, 14.5f)
            lineTo(12f, 18.5f)
            lineTo(16f, 14.5f)
        }
    }

    /** Equivale ao "clock.fill" do iOS. */
    val Clock: ImageVector by lazy {
        strokeIcon("Clock") {
            circle(12f, 12f, 8.8f)
            moveTo(12f, 7f)
            verticalLineTo(12f)
            lineTo(15.5f, 14f)
        }
    }

    /** Equivale ao "sparkles" do iOS: estrela de quatro pontas com uma menor ao lado. */
    val Sparkles: ImageVector by lazy {
        icon("Sparkles") {
            moveTo(10f, 3f)
            quadTo(11f, 9f, 17f, 10f)
            quadTo(11f, 11f, 10f, 17f)
            quadTo(9f, 11f, 3f, 10f)
            quadTo(9f, 9f, 10f, 3f)
            close()
            moveTo(18f, 14f)
            quadTo(18.5f, 17.5f, 22f, 18f)
            quadTo(18.5f, 18.5f, 18f, 22f)
            quadTo(17.5f, 18.5f, 14f, 18f)
            quadTo(17.5f, 17.5f, 18f, 14f)
            close()
        }
    }

    /** Equivale ao "line.3.horizontal.decrease" do iOS. */
    val Filter: ImageVector by lazy {
        strokeIcon("Filter") {
            moveTo(4f, 7f)
            horizontalLineTo(20f)
            moveTo(7f, 12f)
            horizontalLineTo(17f)
            moveTo(10f, 17f)
            horizontalLineTo(14f)
        }
    }

    /** Equivale ao "camera.fill" do iOS. */
    val Camera: ImageVector by lazy {
        strokeIcon("Camera") {
            moveTo(9f, 4f)
            lineTo(7.2f, 6f)
            horizontalLineTo(4.5f)
            curveTo(3.1f, 6f, 2f, 7.1f, 2f, 8.5f)
            verticalLineTo(17.5f)
            curveTo(2f, 18.9f, 3.1f, 20f, 4.5f, 20f)
            horizontalLineTo(19.5f)
            curveTo(20.9f, 20f, 22f, 18.9f, 22f, 17.5f)
            verticalLineTo(8.5f)
            curveTo(22f, 7.1f, 20.9f, 6f, 19.5f, 6f)
            horizontalLineTo(16.8f)
            lineTo(15f, 4f)
            close()
            moveTo(12f, 9f)
            arcToRelative(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 0f, dy1 = 8f)
            arcToRelative(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 0f, dy1 = -8f)
            close()
        }
    }

    /** Equivale ao "photo" do iOS: moldura com montanha e sol. */
    val Photo: ImageVector by lazy {
        strokeIcon("Photo") {
            moveTo(5.5f, 4.5f)
            horizontalLineTo(18.5f)
            curveTo(19.6f, 4.5f, 20.5f, 5.4f, 20.5f, 6.5f)
            verticalLineTo(17.5f)
            curveTo(20.5f, 18.6f, 19.6f, 19.5f, 18.5f, 19.5f)
            horizontalLineTo(5.5f)
            curveTo(4.4f, 19.5f, 3.5f, 18.6f, 3.5f, 17.5f)
            verticalLineTo(6.5f)
            curveTo(3.5f, 5.4f, 4.4f, 4.5f, 5.5f, 4.5f)
            close()
            moveTo(3.5f, 16f)
            lineTo(8.5f, 11f)
            lineTo(13f, 15.5f)
            lineTo(15.5f, 13f)
            lineTo(20.5f, 18f)
            circle(15.5f, 8.5f, 1.4f)
        }
    }

    /** Equivale ao "pencil" do iOS. */
    val Pencil: ImageVector by lazy {
        strokeIcon("Pencil") {
            moveTo(4f, 20f)
            lineTo(5f, 15.5f)
            lineTo(15.5f, 5f)
            lineTo(19f, 8.5f)
            lineTo(8.5f, 19f)
            close()
            moveTo(13.5f, 7f)
            lineTo(17f, 10.5f)
        }
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
        close()
    }

    private fun strokeIcon(
        name: String,
        pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )
    }.build()

    private fun icon(
        name: String,
        pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply { path(fill = SolidColor(Color.Black), pathBuilder = pathBuilder) }.build()
}
