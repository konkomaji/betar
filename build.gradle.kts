// Root build file. Per-module configuration lives in app/build.gradle.kts.
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Kotlin 2.0+ moved the Compose compiler out of composeOptions{} into its own plugin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
