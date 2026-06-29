plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.shallowdusty.oplusotastudio.core.model"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// core-model is the dependency-inversion seam: it holds the domain models and
// service contracts (interfaces). It has no implementation dependencies so it
// stays pure-JVM unit-testable. Backend modules implement the contracts:
//   // TODO core-ota:     implement OtaLookupService
//   // TODO core-download: implement DownloadEngine
//   // TODO core-storage: implement PackageRepository
dependencies {
    // Flow is used in the DownloadEngine/PackageRepository contracts.
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
