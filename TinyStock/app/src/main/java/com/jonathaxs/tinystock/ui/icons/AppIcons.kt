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
