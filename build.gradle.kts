// Root build file. Per-module configuration lives in app/build.gradle.kts.
plugins {
    id("com.android.application") version "8.7.2" apply false
    // Bumped from 2.0.21, then again from 2.1.20: first Material 3 Expressive (material3
    // 1.4.0, pulled in by the compose-bom bump in app/build.gradle.kts) needed newer than
    // 2.0.21; now fr.acinq.secp256k1:secp256k1-kmp-jni-android:0.24.0 (added for the Nostr
    // bridge) ships kotlin-stdlib metadata version 2.3.0, which 2.1.20's compiler can't read
    // ("can read versions up to 2.2.0"). 2.3.10 is the current stable release that reads that
    // metadata; bumping again rather than pinning secp256k1-kmp older, since this project
    // already prefers "go with what the dependency graph wants" over fighting it.
    // docs/REPRODUCIBLE-BUILD.md's toolchain pin (Rust 1.96.0 / cargo-ndk 4.1.2 / NDK r27c /
    // AGP 8.7.2 / Kotlin 2.0.21 / Gradle 8.11.1) is now stale on the Kotlin line and needs
    // updating there too, plus a fresh same-machine reproducibility re-check -- not done as
    // part of this change, flagged for that document's own pass.
    id("org.jetbrains.kotlin.android") version "2.3.10" apply false
    // Kotlin 2.0+ moved the Compose compiler out of composeOptions{} into its own plugin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
}
