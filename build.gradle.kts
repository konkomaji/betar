// Root build file. Per-module configuration lives in app/build.gradle.kts.
plugins {
    id("com.android.application") version "8.7.2" apply false
    // Bumped from 2.0.21: Material 3 Expressive (material3 1.4.0, pulled in by the
    // compose-bom bump in app/build.gradle.kts) is compiled against a newer Kotlin than
    // 2.0.21 can consume -- compileDebugKotlin failed with "internal in file" errors on
    // MaterialExpressiveTheme/MaterialShapes/MotionScheme, the signature of a Kotlin
    // metadata-version mismatch, not a missing API. 2.1.20 is what Compose's own transitive
    // dependencies (androidx.compose:compose-bom:2026.06.01's graph) already force
    // kotlin-stdlib to when resolved under 2.0.21, so this pins the project to the version
    // the dependency graph already wants rather than fighting it with exclusions.
    // docs/REPRODUCIBLE-BUILD.md's toolchain pin (Rust 1.96.0 / cargo-ndk 4.1.2 / NDK r27c /
    // AGP 8.7.2 / Kotlin 2.0.21 / Gradle 8.11.1) is now stale on the Kotlin line and needs
    // updating there too, plus a fresh same-machine reproducibility re-check -- not done as
    // part of this change, flagged for that document's own pass.
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    // Kotlin 2.0+ moved the Compose compiler out of composeOptions{} into its own plugin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
}
