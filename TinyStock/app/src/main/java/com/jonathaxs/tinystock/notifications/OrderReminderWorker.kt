package com.jonathaxs.tinystock.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jonathaxs.tinystock.MainActivity
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.TinyStockApplication
import com.jonathaxs.tinystock.core.model.OrderReminder
import com.jonathaxs.tinystock.core.model.OrderReminderRoute

/** Chega a hora do lembrete: mostra a notificacao e reabastece a fila. */
class OrderReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val data = inputData
        val id = data.getString(KEY_ID) ?: return Result.success()
        val storeID = data.getString(OrderReminder.KEY_STORE_ID) ?: return Result.success()
        val orderID = data.getString(OrderReminder.KEY_ORDER_ID) ?: return Result.success()
        val kind = OrderReminder.Kind.fromRawValue(data.getString(OrderReminder.KEY_REMINDER_KIND).orEmpty())
        val timing = OrderReminder.Timing.fromRawValue(data.getString(KEY_TIMING).orEmpty())
        if (kind != null && timing != null) {
            ReminderNotifications.show(
                applicationContext,
                id = id,
                storeID = storeID,
                orderID = orderID,
                kind = kind,
                timing = timing,
                storeName = data.getString(KEY_STORE_NAME).orEmpty(),
                productName = data.getString(KEY_PRODUCT_NAME).orEmpty(),
                sound = data.getBoolean(KEY_SOUND, true),
            )
        }
        // Com o app fechado, a fila limitada so anda por aqui.
        (applicationContext as TinyStockApplication).container.reminders.refreshNow()
        return Result.success()
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_TIMING = "timing"
        const val KEY_STORE_NAME = "storeName"
        const val KEY_PRODUCT_NAME = "productName"
        const val KEY_SOUND = "sound"
    }
}

/** Canais, montagem da notificacao e leitura do toque nela. */
object ReminderNotifications {

    private const val CHANNEL_SOUND = "order_reminders"
    private const val CHANNEL_SILENT = "order_reminders_silent"
    private const val EXTRA_ID = "tinystock.reminder.id"

    /** Dois canais porque no Android o som fica no canal, nao em cada aviso. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SOUND, context.getString(R.string.notifications_enabled), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SILENT, context.getString(R.string.notifications_channel_silent), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { setSound(null, null) },
        )
    }

    fun show(
        context: Context,
        id: String,
        storeID: String,
        orderID: String,
        kind: OrderReminder.Kind,
        timing: OrderReminder.Timing,
        storeName: String,
        productName: String,
        sound: Boolean,
    ) {
        val allowed = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return

        // O texto e montado agora, no idioma atual do aparelho. Sem nome ou dados do comprador.
        val product = productName.ifBlank { context.getString(R.string.reminder_product_fallback) }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ID, id)
            putExtra(OrderReminder.KEY_STORE_ID, storeID)
            putExtra(OrderReminder.KEY_ORDER_ID, orderID)
            putExtra(OrderReminder.KEY_REMINDER_KIND, kind.rawValue)
        }
        val tap = PendingIntent.getActivity(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, if (sound) CHANNEL_SOUND else CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title(kind, timing)))
            .setContentText(context.getString(R.string.reminder_body, storeName, product))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setGroup("tinystock.store.$storeID")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, 0, notification)
        } catch (_: SecurityException) {
            // Permissao retirada entre a conferencia e o aviso.
        }
    }

    /** Destino do toque na notificacao, conferido como no iOS; null se o intent nao e de lembrete. */
    fun route(intent: Intent?): OrderReminderRoute? {
        val extras = intent?.extras ?: return null
        val id = extras.getString(EXTRA_ID) ?: return null
        val info = listOf(OrderReminder.KEY_STORE_ID, OrderReminder.KEY_ORDER_ID, OrderReminder.KEY_REMINDER_KIND)
            .associateWith { extras.getString(it) }
        return OrderReminderRoute.from(id, info)
    }

    /** Remove os campos do lembrete para o mesmo toque nao abrir o pedido de novo (ex.: ao girar a tela). */
    fun consume(intent: Intent?) {
        intent?.removeExtra(EXTRA_ID)
    }

    private fun title(kind: OrderReminder.Kind, timing: OrderReminder.Timing): Int = when (kind) {
        OrderReminder.Kind.Production -> when (timing) {
            OrderReminder.Timing.DayBefore -> R.string.reminder_production_tomorrow
            OrderReminder.Timing.DueDay -> R.string.reminder_production_today
            OrderReminder.Timing.Overdue -> R.string.reminder_production_overdue
        }
        OrderReminder.Kind.Shipping -> when (timing) {
            OrderReminder.Timing.DayBefore -> R.string.reminder_shipping_tomorrow
            OrderReminder.Timing.DueDay -> R.string.reminder_shipping_today
            OrderReminder.Timing.Overdue -> R.string.reminder_shipping_overdue
        }
    }
}
