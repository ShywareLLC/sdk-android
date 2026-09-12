pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Supplies these plugins' versions for standalone builds of this module
    // (build.gradle.kts intentionally applies them without a version so
    // they don't conflict when included as a subproject by a consumer app
    // that already manages its own plugin versions -- see build.gradle.kts).
    plugins {
        id("com.android.library") version "9.1.0"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "shyware-sdk-android"
