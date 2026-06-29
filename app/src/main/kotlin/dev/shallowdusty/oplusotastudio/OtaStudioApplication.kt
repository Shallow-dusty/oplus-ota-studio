package dev.shallowdusty.oplusotastudio

import android.app.Application

class OtaStudioApplication : Application() {
    val graph: AppGraph by lazy { AppGraph() }
}
