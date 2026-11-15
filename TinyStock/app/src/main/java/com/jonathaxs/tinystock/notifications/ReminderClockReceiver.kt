package com.jonathaxs.tinystock.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jonathaxs.tinystock.TinyStockApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Hora ou fuso do aparelho mudou: os lembretes guardam quanto falta, entao a fila e refeita. Reiniciar o
 * aparelho nao precisa disso, o WorkManager ja refaz a fila sozinho.
 */
class ReminderClockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val pending = goAsync()
        val reminders = (context.applicationContext as TinyStockApplication).container.reminders
        CoroutineScope(Dispatchers.Default).launch {
            try {
                reminders.rebuild()
            } finally {
                pending.finish()
            }
        }
    }
}
