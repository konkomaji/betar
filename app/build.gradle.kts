plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "india.projectmesh.app"
    compileSdk = 35 // Android 15

    defaultConfig {
        applicationId = "india.projectmesh.app"
        // Floor chosen for the "inexpensive Android phones (~2 GB RAM)" target audience in
        // docs/LOCALIZATION-UX.md, not for API surface reasons.
        minSdk = 26
        targetSdk = 35 // Android 15
        versionCode = 1
        versionName = "0.1.0-prealpha"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // reproducible builds (docs/DISTRIBUTION.md) come before shrinking
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes.add("META-INF/*")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Provides the XML Theme.Material3.* base themes (manifest android:theme) -- Compose
    // Material3 alone only supplies the in-Compose theming, not these launch-time resources.
    implementation("com.google.android.material:material:1.12.0")
    // Required at runtime by UniFFI-generated Kotlin bindings (JNA-based FFI, not raw JNI).
    implementation("net.java.dev.jna:jna:5.15.0@aar")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
