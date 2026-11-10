package com.jonathaxs.tinystock.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.ui.components.AppGroupedCard
import com.jonathaxs.tinystock.ui.components.AppPage
import com.jonathaxs.tinystock.ui.components.AppSectionLabel
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.components.CircleIcon
import com.jonathaxs.tinystock.ui.components.StoreImage
import com.jonathaxs.tinystock.ui.icons.AppIcons
import com.jonathaxs.tinystock.ui.theme.color

/**
 * Paginas dos Ajustes, na ordem da lista. Porte do `SettingsPage` do iOS, sem o Calendario do iPhone
 * (no Android a exportacao de evento sera decidida depois).
 */
enum class SettingsPage(val titleRes: Int) {
    Stores(R.string.stores_title),
    Notifications(R.string.notifications_title),
    Backup(R.string.settings_backup_title),
    Colors(R.string.settings_color_title),
    About(R.string.settings_about_title),
    Privacy(R.string.settings_privacy_title);

    val icon: ImageVector
        get() = when (this) {
            Stores -> AppIcons.Storefront
            Notifications -> Icons.Filled.Notifications
            Backup -> AppIcons.Drive
            Colors -> AppIcons.Palette
            About -> Icons.Filled.Info
            Privacy -> Icons.Filled.Lock
        }

    /** Mesmas cores de sistema do iOS. */
    fun color(dark: Boolean): Color = when (this) {
        Stores -> AppAccentColor.Orange.color(dark)
        Notifications -> AppAccentColor.Red.color(dark)
        Backup, About -> systemGray(dark)
        Colors -> AppAccentColor.Pink.color(dark)
        Privacy -> AppAccentColor.Blue.color(dark)
    }
}

private fun systemGray(dark: Boolean) = Color(if (dark) 0xFF98989D else 0xFF8E8E93)

/** Recuo do divisor: borda do card + icone + espaco, alinhado ao texto. */
private val RowDividerInset = 62.dp

/** Ajustes como pagina: lojas, sistema, aparencia e sobre. Porte do `SettingsView` do iOS (C5). */
@Composable
fun SettingsScreen(selectedStore: StoreProfile?, onOpen: (SettingsPage) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary

    AppPage(stringResource(R.string.tab_settings), onBack) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = AppStyle.HorizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("stores") {
                Section(stringResource(R.string.stores_title), accent) {
                    listOf { StoreRow(selectedStore) { onOpen(SettingsPage.Stores) } }
                }
            }
            item("system") {
                Section(stringResource(R.string.settings_section_system), accent) {
                    listOf<@Composable () -> Unit>(
                        { PageRow(SettingsPage.Notifications, onOpen) },
                        { PageRow(SettingsPage.Backup, onOpen) },
                        { LanguageRow { openLanguageSettings(context) } },
                    )
                }
            }
            item("appearance") {
                Section(stringResource(R.string.settings_section_appearance), accent) {
                    listOf { PageRow(SettingsPage.Colors, onOpen) }
                }
            }
            item("about") {
                Section(stringResource(R.string.settings_section_about), accent) {
                    listOf<@Composable () -> Unit>(
                        { PageRow(SettingsPage.About, onOpen) },
                        { PageRow(SettingsPage.Privacy, onOpen) },
                    )
                }
            }
            item("bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun Section(title: String, accent: Color, rows: () -> List<@Composable () -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
        AppSectionLabel(title, color = accent)
        AppGroupedCard(rows(), dividerInset = RowDividerInset)
    }
}

/** Linha clicavel: icone, textos e o indicador a direita (decorativo). */
@Composable
private fun SettingsRow(
    onClick: () -> Unit,
    trailing: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    onClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
        Icon(
            trailing,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(if (trailing == Icons.AutoMirrored.Filled.KeyboardArrowRight) 24.dp else 18.dp),
        )
    }
}

@Composable
private fun PageRow(page: SettingsPage, onOpen: (SettingsPage) -> Unit) {
    val dark = isSystemInDarkTheme()
    SettingsRow(onClick = { onOpen(page) }) {
        CircleIcon(page.icon, page.color(dark))
        Text(stringResource(page.titleRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** Foto e nome da loja atual, com "Gerenciar lojas" embaixo. */
@Composable
private fun StoreRow(store: StoreProfile?, onClick: () -> Unit) {
    SettingsRow(onClick = onClick) {
        StoreImage(store?.imageFileName, side = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(store?.name ?: stringResource(R.string.store_default_name), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_store_manage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Idioma abre a pagina de idioma do app nos ajustes do sistema. */
@Composable
private fun LanguageRow(onClick: () -> Unit) {
    SettingsRow(
        onClick = onClick,
        trailing = AppIcons.OpenExternal,
        onClickLabel = stringResource(R.string.settings_language_hint),
    ) {
        CircleIcon(AppIcons.Globe, AppAccentColor.Blue.color(isSystemInDarkTheme()))
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** Android 13+ tem a pagina de idioma por app; antes disso abre os detalhes do app. */
private fun openLanguageSettings(context: Context) {
    val uri = Uri.fromParts("package", context.packageName, null)
    val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Settings.ACTION_APP_LOCALE_SETTINGS
    } else {
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    }
    runCatching { context.startActivity(Intent(action, uri)) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)) } }
}
