package dev.shallowdusty.oplusotastudio.core.storage

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dev.shallowdusty.oplusotastudio.core.model.LookupPrivacyConsentStore

private const val LOOKUP_PRIVACY_CONSENT_NAME = "lookup_privacy_consent"

private val Context.lookupPrivacyConsentDataStore by preferencesDataStore(
    name = LOOKUP_PRIVACY_CONSENT_NAME,
)

fun createLookupPrivacyConsentStore(context: Context): LookupPrivacyConsentStore =
    DataStoreLookupPrivacyConsentStore(context.applicationContext.lookupPrivacyConsentDataStore)
