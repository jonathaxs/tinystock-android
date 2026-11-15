package com.jonathaxs.tinystock

import android.app.Application
import com.jonathaxs.tinystock.core.data.AppPreferences
import com.jonathaxs.tinystock.core.data.DirectoryImageFileStore
import com.jonathaxs.tinystock.core.data.ImageFileStore
import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.data.room.DatabaseProvider
import com.jonathaxs.tinystock.core.data.room.RoomTinyStockStore
import com.jonathaxs.tinystock.notifications.OrderReminderCoordinator
import com.jonathaxs.tinystock.notifications.ReminderNotifications
import com.jonathaxs.tinystock.notifications.WorkManagerOrderReminderCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Guarda as dependencias do app (sem Hilt/Koin): banco, preferencias, pasta das fotos e lembretes. */
class TinyStockApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannels(this)
        container.reminders.start()
    }
}

class AppContainer(application: Application) {
    val store: TinyStockStore by lazy { RoomTinyStockStore(DatabaseProvider.get(application)) }
    val preferences: AppPreferences by lazy { AppPreferences.from(application) }
    val images: ImageFileStore by lazy { DirectoryImageFileStore(File(application.filesDir, "images")) }

    /** Vive enquanto o processo do app existir, inclusive so para mostrar um lembrete. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val reminders: OrderReminderCoordinator by lazy {
        OrderReminderCoordinator(store, preferences, WorkManagerOrderReminderCenter(application, preferences), appScope)
    }
}

/** Atalho para os ViewModels (`AndroidViewModel`) chegarem ao [AppContainer]. */
val Application.container: AppContainer
    get() = (this as TinyStockApplication).container
