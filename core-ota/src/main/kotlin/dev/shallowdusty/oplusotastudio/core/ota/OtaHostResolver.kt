package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaRegion

class OtaHostResolver {
    fun resolve(region: OtaRegion): String =
        when (region) {
            OtaRegion.Global -> "otagm.oppo.com"
            OtaRegion.India -> "otadiu.oppo.com"
            OtaRegion.International -> "otai.oppo.com"
            OtaRegion.China -> "otacn.oppo.com"
        }
}
