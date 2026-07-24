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
    // Offline maps (FEATURES.md §3): renders entirely from a local style + local tile sources,
    // no remote style/tile server required -- matches this app's "no INTERNET permission" stance
    // (AndroidManifest.xml). This pass wires up the SDK itself; real MBTiles/PMTiles regional
    // tile packs are a separate follow-up (see MapScreen.kt's doc comment).
    implementation("org.maplibre.gl:android-sdk:11.13.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

// XML forbids "--" inside a <!-- --> comment body -- this project has hit that exact mistake
// four times (AndroidManifest.xml x2, strings.xml, AndroidManifest.xml again), each time as a
// cryptic ManifestMerger2/resource-parse failure with no line number pointing at the real cause.
// Checked here, before any resource/manifest processing runs, so it fails fast with an exact
// file:line instead. Only flags "--" found strictly *inside* a comment's <!-- ... --> body (the
// delimiters themselves are excluded from the check), so "--" used as a prose dash inside a
// <string> resource's text content -- which is valid XML -- is not a false positive.
tasks.register("checkXmlComments") {
    group = "verification"
    description = "Fails if any XML comment under src/main contains a literal '--' (invalid XML)."

    val xmlFiles = fileTree(layout.projectDirectory.dir("src/main")) { include("**/*.xml") }
    inputs.files(xmlFiles)

    doLast {
        val commentBody = Regex("<!--(.*?)-->", RegexOption.DOT_MATCHES_ALL)
        val violations = mutableListOf<String>()
        xmlFiles.forEach { file ->
            val text = file.readText()
            for (match in commentBody.findAll(text)) {
                val body = match.groupValues[1]
                var searchFrom = 0
                while (true) {
                    val hit = body.indexOf("--", searchFrom)
                    if (hit < 0) break
                    val offset = match.range.first + "<!--".length + hit
                    val line = text.substring(0, offset).count { it == '\n' } + 1
                    violations += "${file.relativeTo(projectDir)}:$line"
                    searchFrom = hit + 1
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Found '--' inside an XML comment (invalid XML -- breaks the manifest/resource " +
                    "parser with a much less useful error than this one). Use ':', ';', or an " +
                    "em dash instead of '--' as a prose dash inside XML comments.\n" +
                    violations.joinToString("\n") { "  $it" },
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn("checkXmlComments")
}
