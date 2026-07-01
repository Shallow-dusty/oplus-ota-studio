plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigningValues = mapOf(
    "storeFile" to (
        providers.gradleProperty("oplusOtaStudio.releaseStoreFile").orNull
            ?: providers.environmentVariable("OPLUS_OTA_STUDIO_RELEASE_STORE_FILE").orNull
        ),
    "storePassword" to (
        providers.gradleProperty("oplusOtaStudio.releaseStorePassword").orNull
            ?: providers.environmentVariable("OPLUS_OTA_STUDIO_RELEASE_STORE_PASSWORD").orNull
        ),
    "keyAlias" to (
        providers.gradleProperty("oplusOtaStudio.releaseKeyAlias").orNull
            ?: providers.environmentVariable("OPLUS_OTA_STUDIO_RELEASE_KEY_ALIAS").orNull
        ),
    "keyPassword" to (
        providers.gradleProperty("oplusOtaStudio.releaseKeyPassword").orNull
            ?: providers.environmentVariable("OPLUS_OTA_STUDIO_RELEASE_KEY_PASSWORD").orNull
        ),
)

val hasAnyReleaseSigningValue = releaseSigningValues.values.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigning = releaseSigningValues.values.all { !it.isNullOrBlank() }

if (hasAnyReleaseSigningValue && !hasCompleteReleaseSigning) {
    error(
        "Release signing is partially configured. Set all oplusOtaStudio.release* " +
            "Gradle properties or all OPLUS_OTA_STUDIO_RELEASE_* environment variables.",
    )
}

android {
    namespace = "dev.shallowdusty.oplusotastudio"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.shallowdusty.oplusotastudio"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("release") {
                storeFile = file(releaseSigningValues.getValue("storeFile")!!)
                storePassword = releaseSigningValues.getValue("storePassword")
                keyAlias = releaseSigningValues.getValue("keyAlias")
                keyPassword = releaseSigningValues.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    adbOptions {
        installOptions.add("-g")
    }
}

dependencies {
    implementation(project(":feature-lookup"))
    implementation(project(":feature-downloads"))
    implementation(project(":core-model"))
    implementation(project(":core-download"))
    implementation(project(":core-ota"))
    implementation(project(":core-storage"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.work.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.okhttp)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
