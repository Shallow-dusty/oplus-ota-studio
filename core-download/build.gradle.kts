plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.shallowdusty.oplusotastudio.core.download"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":core-model"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
