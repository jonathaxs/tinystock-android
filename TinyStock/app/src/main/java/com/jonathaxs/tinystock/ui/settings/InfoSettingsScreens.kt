package com.jonathaxs.tinystock.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.ui.components.AppGroupedCard
import com.jonathaxs.tinystock.ui.components.AppPage
import com.jonathaxs.tinystock.ui.components.AppSectionLabel
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.components.CircleIcon
import com.jonathaxs.tinystock.ui.components.appCard
import com.jonathaxs.tinystock.ui.icons.AppIcons
import com.jonathaxs.tinystock.ui.products.SwitchRow
import com.jonathaxs.tinystock.ui.theme.color

private const val FEEDBACK_EMAIL = "contato@jonathasmotta.com"
private const val WEBSITE_URL = "https://jonathasmotta.com"

// region Pecas comuns das paginas dos Ajustes

/** Rotulo, card agrupado e o rodape opcional, como uma `Section` do iOS. */
@Composable
internal fun SettingsCardSection(
    title: String?,
    rows: List<@Composable () -> Unit>,
    footer: String? = null,
    dividerInset: androidx.compose.ui.unit.Dp = 62.dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 18.dp)) {
        title?.let { AppSectionLabel(it, color = MaterialTheme.colorScheme.primary) }
        AppGroupedCard(rows, dividerInset = dividerInset)
        footer?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/** Linha de acao com icone no circulo; [trailing] e decorativo (ex.: abre fora do app). */
@Composable
internal fun SettingsActionRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    enabled: Boolean = true,
    trailing: ImageVector? = null,
    hint: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = hint, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircleIcon(icon, iconColor)
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing?.let {
            Icon(
                it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Bloco de texto com icone e titulo, lido de uma vez pelo TalkBack. */
@Composable
private fun InfoCard(icon: ImageVector, iconColor: Color, title: String, message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .appCard()
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleIcon(icon, iconColor)
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScrollPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    AppPage(title, onBack) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppStyle.HorizontalPadding, vertical = 16.dp),
        ) { content() }
    }
}

// endregion

/** Exportacao para a agenda do aparelho. Porte do `CalendarExportSettingsView` do iOS. */
@Composable
fun CalendarExportSettingsScreen(isEnabled: Boolean, onChange: (Boolean) -> Unit, onBack: () -> Unit) {
    val dark = isSystemInDarkTheme()
    SettingsScrollPage(stringResource(R.string.settings_calendar_title), onBack) {
        SettingsCardSection(
            title = stringResource(R.string.settings_calendar_export_section),
            footer = stringResource(R.string.settings_calendar_showExport_footer),
            rows = listOf {
                SwitchRow(stringResource(R.string.settings_calendar_showExport), isEnabled, enabled = true, onCheckedChange = onChange)
            },
        )
        InfoCard(
            icon = Icons.Filled.Lock,
            iconColor = AppAccentColor.Blue.color(dark),
            title = stringResource(R.string.settings_calendar_privacy),
            message = stringResource(R.string.settings_calendar_privacy_message),
        )
    }
}

/** Icone, versao, descricao e contato. Porte do `AboutView` do iOS. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val version = remember { appVersion(context) }
    SettingsScrollPage(stringResource(R.string.settings_about_title), onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painterResource(R.drawable.ic_launcher_art),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Text(stringResource(R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.settings_about_versionBuild, version.first, version.second),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.settings_about_description),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp, start = 8.dp, end = 8.dp),
            )
        }
        SettingsCardSection(
            title = stringResource(R.string.settings_about_developer),
            rows = listOf(
                {
                    SettingsActionRow(
                        icon = Icons.Filled.Email,
                        iconColor = AppAccentColor.Blue.color(dark),
                        title = stringResource(R.string.settings_about_feedback),
                        trailing = AppIcons.OpenExternal,
                        onClick = { open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$FEEDBACK_EMAIL"))) },
                    )
                },
                {
                    SettingsActionRow(
                        icon = AppIcons.Globe,
                        iconColor = AppAccentColor.Blue.color(dark),
                        title = stringResource(R.string.settings_about_website),
                        trailing = AppIcons.OpenExternal,
                        onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL))) },
                    )
                },
            ),
        )
    }
}

/** Como o app trata os dados. Porte do `PrivacyView` do iOS, com os textos do Android. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val dark = isSystemInDarkTheme()
    SettingsScrollPage(stringResource(R.string.settings_privacy_title), onBack) {
        InfoCard(
            icon = Icons.Filled.Lock,
            iconColor = AppAccentColor.Blue.color(dark),
            title = stringResource(R.string.settings_privacy_local_title),
            message = stringResource(R.string.settings_privacy_local_message),
        )
        InfoCard(
            icon = AppIcons.Drive,
            iconColor = Color(if (dark) 0xFF98989D else 0xFF8E8E93),
            title = stringResource(R.string.settings_privacy_icloud_title),
            message = stringResource(R.string.settings_privacy_icloud_message),
        )
        InfoCard(
            icon = Icons.Filled.Notifications,
            iconColor = AppAccentColor.Red.color(dark),
            title = stringResource(R.string.settings_privacy_notifications_title),
            message = stringResource(R.string.settings_privacy_notifications_message),
        )
    }
}

/** Versao e build do app, como "1.0 (1)". */
private fun appVersion(context: Context): Pair<String, String> = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    (info.versionName ?: "-") to PackageInfoCompat.getLongVersionCode(info).toString()
}.getOrDefault("-" to "-")

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Sem app de e-mail ou navegador: nada a fazer.
    }
}
