package com.jonathaxs.tinystock.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.TinyStockApplication
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.core.model.OrderReminderIssue
import com.jonathaxs.tinystock.core.model.OrderReminderSettings
import com.jonathaxs.tinystock.core.service.OrderReminderAuthorization
import com.jonathaxs.tinystock.notifications.OrderReminderCoordinator
import com.jonathaxs.tinystock.ui.components.AppPage
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.icons.AppIcons
import com.jonathaxs.tinystock.ui.products.SwitchRow
import com.jonathaxs.tinystock.ui.theme.color
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Recuo do divisor nas linhas sem icone. */
private val PlainDividerInset = 16.dp

/** Lembretes de pedidos: ligar, permissao, etapas, quando e som. Porte do `NotificationSettingsView` do iOS. */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coordinator = remember { (context.applicationContext as TinyStockApplication).container.reminders }
    val state by coordinator.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isRequestingPermission by remember { mutableStateOf(false) }
    var editingTime by rememberSaveable { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch {
            coordinator.onPermissionResult()
            isRequestingPermission = false
        }
    }

    /** Pede a permissao do sistema so quando ainda nao foi pedida (Android 13+). */
    fun requestPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (coordinator.state.value.authorization != OrderReminderAuthorization.NotDetermined) return
        isRequestingPermission = true
        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun setEnabled(enabled: Boolean) {
        scope.launch {
            coordinator.setEnabled(enabled)
            if (enabled) requestPermissionIfNeeded()
        }
    }

    LaunchedEffect(Unit) { coordinator.requestRefresh() }

    val settings = state.settings
    val enabled = settings.isEnabled
    val busy = isRequestingPermission || !state.isLoaded
    val dark = isSystemInDarkTheme()

    AppPage(stringResource(R.string.notifications_title), onBack) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppStyle.HorizontalPadding, vertical = 16.dp),
        ) {
            SettingsCardSection(
                title = null,
                footer = stringResource(R.string.notifications_scope),
                dividerInset = PlainDividerInset,
                rows = listOf {
                    SwitchRow(stringResource(R.string.notifications_enabled), enabled, enabled = !busy, onCheckedChange = ::setEnabled)
                },
            )

            SettingsCardSection(
                title = stringResource(R.string.notifications_permission),
                rows = buildList {
                    add {
                        ValueRow(stringResource(R.string.notifications_permission_status), authorizationText(state.authorization))
                    }
                    if (enabled && state.authorization == OrderReminderAuthorization.NotDetermined) {
                        add {
                            SettingsActionRow(
                                icon = Icons.Filled.Notifications,
                                iconColor = AppAccentColor.Red.color(dark),
                                title = stringResource(R.string.notifications_permission_request),
                                enabled = !busy,
                                onClick = ::requestPermissionIfNeeded,
                            )
                        }
                    }
                    add {
                        SettingsActionRow(
                            icon = Icons.Filled.Settings,
                            iconColor = Color(if (dark) 0xFF98989D else 0xFF8E8E93),
                            title = stringResource(R.string.notifications_permission_settings),
                            trailing = AppIcons.OpenExternal,
                            onClick = { openNotificationSettings(context) },
                        )
                    }
                },
            )

            Disabled(!enabled) {
                SettingsCardSection(
                    title = stringResource(R.string.notifications_stages),
                    dividerInset = PlainDividerInset,
                    rows = listOf(
                        { PreferenceSwitch(R.string.notifications_production, settings.productionEnabled, enabled, coordinator) { s, v -> s.copy(productionEnabled = v) } },
                        { PreferenceSwitch(R.string.notifications_shipping, settings.shippingEnabled, enabled, coordinator) { s, v -> s.copy(shippingEnabled = v) } },
                    ),
                )

                SettingsCardSection(
                    title = stringResource(R.string.notifications_when),
                    footer = stringResource(R.string.notifications_time_footer),
                    dividerInset = PlainDividerInset,
                    rows = listOf(
                        { PreferenceSwitch(R.string.notifications_dayBefore, settings.dayBeforeEnabled, enabled, coordinator) { s, v -> s.copy(dayBeforeEnabled = v) } },
                        { PreferenceSwitch(R.string.notifications_dueDay, settings.dueDayEnabled, enabled, coordinator) { s, v -> s.copy(dueDayEnabled = v) } },
                        { PreferenceSwitch(R.string.notifications_overdue, settings.overdueEnabled, enabled, coordinator) { s, v -> s.copy(overdueEnabled = v) } },
                        {
                            ValueRow(
                                title = stringResource(R.string.notifications_time),
                                value = formatTime(context, settings.hour, settings.minute),
                                onClick = if (enabled) ({ editingTime = true }) else null,
                            )
                        },
                    ),
                )

                SettingsCardSection(
                    title = null,
                    dividerInset = PlainDividerInset,
                    rows = listOf {
                        PreferenceSwitch(R.string.notifications_sound, settings.soundEnabled, enabled, coordinator) { s, v -> s.copy(soundEnabled = v) }
                    },
                )
            }

            if (enabled && state.authorization.allowsScheduling) {
                SettingsCardSection(
                    title = null,
                    footer = if (state.deferredCount > 0) stringResource(R.string.notifications_capacity_footer) else null,
                    dividerInset = PlainDividerInset,
                    rows = buildList {
                        add { ValueRow(stringResource(R.string.notifications_scheduled), state.scheduledCount.toString()) }
                        if (state.deferredCount > 0) {
                            add { ValueRow(stringResource(R.string.notifications_deferred), state.deferredCount.toString()) }
                        }
                    },
                )
            }

            state.issue?.let { issue ->
                SettingsCardSection(
                    title = null,
                    rows = listOf(
                        { IssueRow(issueText(issue)) },
                        {
                            SettingsActionRow(
                                icon = Icons.Filled.Refresh,
                                iconColor = AppAccentColor.Blue.color(dark),
                                title = stringResource(R.string.notifications_retry),
                                enabled = !busy && !state.isSynchronizing,
                                onClick = {
                                    scope.launch {
                                        if (issue == OrderReminderIssue.Permission) setEnabled(coordinator.state.value.settings.isEnabled)
                                        else coordinator.refreshNow()
                                    }
                                },
                            )
                        },
                    ),
                )
            }
        }
    }

    if (editingTime) {
        TimeDialog(
            hour = settings.hour,
            minute = settings.minute,
            onDismiss = { editingTime = false },
            onConfirm = { hour, minute ->
                editingTime = false
                scope.launch { coordinator.update { it.copy(hour = hour, minute = minute) } }
            },
        )
    }
}

/** Interruptor de uma preferencia; grava pela versao mais recente das preferencias. */
@Composable
private fun PreferenceSwitch(
    label: Int,
    checked: Boolean,
    enabled: Boolean,
    coordinator: OrderReminderCoordinator,
    change: (OrderReminderSettings, Boolean) -> OrderReminderSettings,
) {
    val scope = rememberCoroutineScope()
    SwitchRow(stringResource(label), checked, enabled = enabled) { value ->
        scope.launch { coordinator.update { change(it, value) } }
    }
}

/** Titulo e valor, lidos juntos; com [onClick] vira botao (ex.: horario). */
@Composable
private fun ValueRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base.semantics(mergeDescendants = true) { })
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IssueRow(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Secoes desligadas ficam apagadas, como o `.disabled` do iOS. */
@Composable
private fun Disabled(disabled: Boolean, content: @Composable () -> Unit) {
    Column(Modifier.alpha(if (disabled) 0.5f else 1f)) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val picker = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notifications_time)) },
        text = { TimePicker(state = picker) },
        confirmButton = {
            TextButton(onClick = { onConfirm(picker.hour, picker.minute) }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun authorizationText(authorization: OrderReminderAuthorization): String = stringResource(
    when (authorization) {
        OrderReminderAuthorization.NotDetermined -> R.string.notifications_authorization_notDetermined
        OrderReminderAuthorization.Denied -> R.string.notifications_authorization_denied
        OrderReminderAuthorization.Authorized -> R.string.notifications_authorization_authorized
        OrderReminderAuthorization.Provisional -> R.string.notifications_authorization_provisional
        OrderReminderAuthorization.Unknown -> R.string.notifications_authorization_unknown
    },
)

@Composable
private fun issueText(issue: OrderReminderIssue): String = stringResource(
    when (issue) {
        OrderReminderIssue.Preferences -> R.string.notifications_error_preferences
        OrderReminderIssue.Permission -> R.string.notifications_error_permission
        OrderReminderIssue.DataRead -> R.string.notifications_error_data
        OrderReminderIssue.Scheduling -> R.string.notifications_error_scheduling
    },
)

/** Horario no formato do aparelho (24 h ou AM/PM). */
private fun formatTime(context: Context, hour: Int, minute: Int): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern))
}

/** Pagina de notificacoes do app nos ajustes do sistema. */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}
