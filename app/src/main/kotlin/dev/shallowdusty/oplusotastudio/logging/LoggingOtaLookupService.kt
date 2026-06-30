package dev.shallowdusty.oplusotastudio.logging

import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupService
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile

class LoggingOtaLookupService(
    private val delegate: OtaLookupService,
    private val logger: AppLogger,
) : OtaLookupService {

    override suspend fun lookup(profile: OtaProfile): OtaLookupResult {
        logger.info(
            tag = Tag,
            message = "lookup started model=${profile.model} region=${profile.region} otaVersion=${profile.otaVersion}",
        )
        return when (val result = delegate.lookup(profile)) {
            is OtaLookupResult.PackageFound -> {
                val pkg = result.pkg
                logger.info(
                    tag = Tag,
                    message = "package found version=${pkg.versionName} host=${pkg.sourceHost} sizeBytes=${pkg.sizeBytes}",
                )
                result
            }
            OtaLookupResult.NoUpdate -> {
                logger.info(tag = Tag, message = "no update")
                result
            }
            is OtaLookupResult.Error -> {
                logger.error(tag = Tag, message = "lookup failed category=${result.category}")
                result
            }
        }
    }

    companion object {
        private const val Tag = "OTA"
    }
}
