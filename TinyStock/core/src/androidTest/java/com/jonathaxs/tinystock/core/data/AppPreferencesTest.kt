package com.jonathaxs.tinystock.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jonathaxs.tinystock.core.model.AppAccentColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Testes instrumentados de [AppPreferences] sobre um arquivo temporario do DataStore. */
@RunWith(AndroidJUnit4::class)
class AppPreferencesTest {

    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        scope = CoroutineScope(Dispatchers.IO + Job())
        file = File(context.cacheDir, "app_prefs_${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        prefs = AppPreferences(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun selecaoVaziaVoltaNullESaveLoadPersisteUuid() = runBlocking {
        assertNull(prefs.load())

        val id = UUID.randomUUID()
        prefs.save(id)
        assertEquals(id, prefs.load())
    }

    @Test
    fun corDeDestaquePadraoCianoGravaRosaEDesconhecidoVoltaCiano() = runBlocking {
        assertEquals(AppAccentColor.Cyan, prefs.accentColor.first())

        prefs.setAccentColor(AppAccentColor.Pink)
        assertEquals(AppAccentColor.Pink, prefs.accentColor.first())

        dataStore.edit { it[stringPreferencesKey(AppAccentColor.STORAGE_KEY)] = "desconhecida" }
        assertEquals(AppAccentColor.Cyan, prefs.accentColor.first())
    }

    @Test
    fun welcomePadraoFalseEDepoisTrue() = runBlocking {
        assertFalse(prefs.didCompleteWelcome.first())

        prefs.setDidCompleteWelcome(true)
        assertTrue(prefs.didCompleteWelcome.first())
    }
}
