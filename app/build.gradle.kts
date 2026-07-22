plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
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
    // Required at runtime by UniFFI-generated Kotlin bindings (JNA-based FFI, not raw JNI).
    implementation("net.java.dev.jna:jna:5.15.0@aar")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
