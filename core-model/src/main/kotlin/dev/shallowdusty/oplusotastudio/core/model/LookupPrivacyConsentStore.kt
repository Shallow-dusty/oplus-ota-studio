package dev.shallowdusty.oplusotastudio.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface LookupPrivacyConsentStore {
    val accepted: Flow<Boolean>

    suspend fun accept()
}

object AlwaysAcceptedLookupPrivacyConsentStore : LookupPrivacyConsentStore {
    override val accepted: Flow<Boolean> = flowOf(true)

    override suspend fun accept() = Unit
}
