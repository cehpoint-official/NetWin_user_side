// settings.gradle.kts

pluginManagement {
    repositories {
        gradlePluginPortal()
        google {
            content {
                // FIX: Added the group ID for ML Kit dependencies.
                // This allows the com.google.mlkit group (GenAI APIs) to be downloaded.
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroup("com.google.mlkit")
            }
        }
        mavenCentral()
    }
}
dependencyResolutionManagement {
    // This section is correctly configured to use Google/Maven repos.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
//        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "NetWin"
include(":app")
// ⭐️ THIS IS THE MISSING LINE THAT FIXES YOUR ERROR
include(":core:utils")
