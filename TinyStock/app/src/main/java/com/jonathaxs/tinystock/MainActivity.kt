package com.jonathaxs.tinystock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jonathaxs.tinystock.notifications.ReminderNotifications
import com.jonathaxs.tinystock.ui.main.TinyStockApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Recriada (ex.: giro da tela) com o mesmo intent: o toque no lembrete ja foi tratado.
        if (savedInstanceState == null) handleReminder(intent)
        setContent { TinyStockApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminder(intent)
    }

    override fun onResume() {
        super.onResume()
        // Volta dos ajustes do sistema ou de outro app: permissao, data ou dados podem ter mudado.
        (application as TinyStockApplication).container.reminders.requestRefresh()
    }

    private fun handleReminder(intent: Intent?) {
        val route = ReminderNotifications.route(intent) ?: return
        ReminderNotifications.consume(intent)
        (application as TinyStockApplication).container.reminders.open(route)
    }
}
