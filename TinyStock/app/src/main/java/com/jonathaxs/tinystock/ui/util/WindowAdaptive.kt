package com.jonathaxs.tinystock.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** Largura minima (smallest width) que classifica o aparelho como tablet (convencao sw600dp). */
private const val TABLET_SMALLEST_WIDTH_DP = 600

/** True quando o menor lado da tela e de tablet. */
@Composable
fun rememberIsTablet(): Boolean =
    LocalConfiguration.current.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP

/** Celular fica sempre em retrato, como o iPhone. Tablet gira livre. */
@Composable
fun ApplyOrientationLock() {
    val context = LocalContext.current
    val isTablet = rememberIsTablet()
    DisposableEffect(isTablet) {
        context.findActivity()?.requestedOrientation =
            if (isTablet) ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            else ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        onDispose { }
    }
}

/** Sobe a cadeia de ContextWrapper ate achar a Activity (ou null fora dela). */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
