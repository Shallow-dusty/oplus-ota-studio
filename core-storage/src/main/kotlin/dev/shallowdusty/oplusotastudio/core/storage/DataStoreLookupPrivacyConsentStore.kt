package dev.shallowdusty.oplusotastudio.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.shallowdusty.oplusotastudio.core.model.LookupPrivacyConsentStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreLookupPrivacyConsentStore(
    private val dataStore: DataStore<Preferences>,
) : LookupPrivacyConsentStore {
    override val accepted: Flow<Boolean> =
        dataStore.data.map { values -> values[AcceptedKey] ?: false }

    override suspend fun accept() {
        dataStore.edit { values ->
            values[AcceptedKey] = true
        }
    }

    private companion object {
        val AcceptedKey = booleanPreferencesKey("lookup_privacy_disclosure_accepted")
    }
}
