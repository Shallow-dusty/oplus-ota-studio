package dev.shallowdusty.oplusotastudio.core.storage

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferencesStore

private const val DOWNLOAD_PREFERENCES_NAME = "download_preferences"

private val Context.downloadPreferencesDataStore by preferencesDataStore(
    name = DOWNLOAD_PREFERENCES_NAME,
)

fun createDownloadPreferencesStore(context: Context): DownloadPreferencesStore =
    DataStoreDownloadPreferencesStore(context.applicationContext.downloadPreferencesDataStore)
