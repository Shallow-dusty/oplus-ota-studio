pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com.android.*")
                includeGroupByRegex("com.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "oplus-ota-studio"

// Frontend modules (v0.0). Backend modules core-ota / core-download / core-storage
// will be included by codex once they land.
include(":app")
include(":core-model")
include(":core-download")
include(":core-ota")
include(":core-storage")
include(":feature-lookup")
include(":feature-downloads")
