package com.jonathaxs.tinystock

import android.app.Application
import com.jonathaxs.tinystock.core.data.AppPreferences
import com.jonathaxs.tinystock.core.data.DirectoryImageFileStore
import com.jonathaxs.tinystock.core.data.ImageFileStore
import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.data.room.DatabaseProvider
import com.jonathaxs.tinystock.core.data.room.RoomTinyStockStore
import java.io.File

/** Guarda as dependencias do app (sem Hilt/Koin): banco, preferencias e pasta das fotos. */
class TinyStockApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val store: TinyStockStore by lazy { RoomTinyStockStore(DatabaseProvider.get(application)) }
    val preferences: AppPreferences by lazy { AppPreferences.from(application) }
    val images: ImageFileStore by lazy { DirectoryImageFileStore(File(application.filesDir, "images")) }
}

/** Atalho para os ViewModels (`AndroidViewModel`) chegarem ao [AppContainer]. */
val Application.container: AppContainer
    get() = (this as TinyStockApplication).container
