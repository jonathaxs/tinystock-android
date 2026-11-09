package com.jonathaxs.tinystock.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
