// Top-level build file. Plugin versions are declared here as apply=false so
// each module applies only what it needs. The version catalog (libs) is picked
// up automatically from gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
