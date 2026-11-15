package com.jonathaxs.tinystock.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.jonathaxs.tinystock.core.data.AppPreferences
import com.jonathaxs.tinystock.core.model.OrderReminder
import com.jonathaxs.tinystock.core.service.OrderReminderAuthorization
import com.jonathaxs.tinystock.core.service.OrderReminderCenter
import com.jonathaxs.tinystock.core.service.PendingOrderReminder
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

/**
 * Fila de lembretes no WorkManager: cada lembrete e um trabalho unico com o id do lembrete, que espera ate a
 * hora e entao mostra a notificacao. O WorkManager guarda a fila no aparelho e a refaz depois de reiniciar.
 * Equivale ao `SystemOrderReminderCenter` do iOS.
 */
class WorkManagerOrderReminderCenter(
    private val context: Context,
    private val preferences: AppPreferences,
) : OrderReminderCenter {

    private val workManager get() = WorkManager.getInstance(context)

    override suspend fun authorization(): OrderReminderAuthorization {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) return OrderReminderAuthorization.Authorized
        // Antes do Android 13 nao ha pedido: desligado so pode ser nos ajustes do sistema.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return OrderReminderAuthorization.Denied
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return when {
            granted -> OrderReminderAuthorization.Denied
            preferences.notificationPermissionRequested() -> OrderReminderAuthorization.Denied
            else -> OrderReminderAuthorization.NotDetermined
        }
    }

    /** O pedido do sistema precisa de uma tela; quem pede e a interface. Aqui so devolve o estado atual. */
    override suspend fun requestAuthorization(): Boolean = authorization().allowsScheduling

    override suspend fun pendingRequests(): List<PendingOrderReminder> =
        workManager.getWorkInfosByTagFlow(TAG).first()
            .filter { it.state == WorkInfo.State.ENQUEUED }
            .mapNotNull { info ->
                val id = info.tags.firstOrNull { it.startsWith(ID_TAG) }?.removePrefix(ID_TAG) ?: return@mapNotNull null
                PendingOrderReminder(id, info.tags.firstOrNull { it.startsWith(SIGNATURE_TAG) }?.removePrefix(SIGNATURE_TAG))
            }

    override suspend fun add(reminder: OrderReminder) {
        val delay = Duration.between(Instant.now(), reminder.fireDate).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<OrderReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(
                workDataOf(
                    OrderReminderWorker.KEY_ID to reminder.id,
                    OrderReminder.KEY_STORE_ID to reminder.storeID.toString(),
                    OrderReminder.KEY_ORDER_ID to reminder.orderID.toString(),
                    OrderReminder.KEY_REMINDER_KIND to reminder.kind.rawValue,
                    OrderReminderWorker.KEY_TIMING to reminder.timing.rawValue,
                    OrderReminderWorker.KEY_STORE_NAME to reminder.storeName,
                    OrderReminderWorker.KEY_PRODUCT_NAME to reminder.productName,
                    OrderReminderWorker.KEY_SOUND to reminder.soundEnabled,
                ),
            )
            .addTag(TAG)
            .addTag(ID_TAG + reminder.id)
            .addTag(SIGNATURE_TAG + reminder.signature())
            .build()
        workManager.enqueueUniqueWork(reminder.id, ExistingWorkPolicy.REPLACE, request).await()
    }

    override suspend fun removePending(identifiers: List<String>) {
        identifiers.forEach { workManager.cancelUniqueWork(it).await() }
    }

    /** Relogio do aparelho mudou: o atraso gravado em cada trabalho ficou errado, entao a fila e refeita. */
    suspend fun removeAll() {
        workManager.cancelAllWorkByTag(TAG).await()
    }

    private companion object {
        const val TAG = "tinystock.order-reminders"
        const val ID_TAG = "id:"
        const val SIGNATURE_TAG = "signature:"
    }
}
