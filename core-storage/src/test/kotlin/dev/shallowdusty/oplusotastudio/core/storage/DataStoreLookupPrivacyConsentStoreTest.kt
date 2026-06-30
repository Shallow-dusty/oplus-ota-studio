package dev.shallowdusty.oplusotastudio.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DataStoreLookupPrivacyConsentStoreTest {

    @Test
    fun `defaults to not accepted`() = runTest {
        val store = DataStoreLookupPrivacyConsentStore(testDataStore("defaults", backgroundScope))

        assertEquals(false, store.accepted.first())
    }

    @Test
    fun `persists accepted disclosure`() = runTest {
        val store = DataStoreLookupPrivacyConsentStore(testDataStore("accepted", backgroundScope))

        store.accept()

        assertEquals(true, store.accepted.first())
    }

    private fun testDataStore(
        name: String,
        scope: CoroutineScope,
    ): DataStore<Preferences> {
        val file = File("build/tmp/datastore-lookup-privacy-consent/$name.preferences_pb")
        file.parentFile?.mkdirs()
        file.delete()
        return PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
    }
}
