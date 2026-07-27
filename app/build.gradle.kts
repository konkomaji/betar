import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Real release signing key, per docs/REPRODUCIBLE-BUILD.md §4's own tracked gap. The keystore
// and its passwords are a genuine secret (losing it means Betar can never be updated under the
// same identity again; leaking it means anyone could sign a malicious update), so they live in
// android/keystore/ and android/keystore.properties, both gitignored, never committed. A
// checkout without that file (any fresh clone, any CI runner) falls back to no release signing
// config at all rather than failing the build, so `assembleDebug` and `core/` work stay
// unaffected.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
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
        versionCode = 4
        versionName = "0.1.3-prealpha"

        // Bengali paused, English-only for now (per user decision, not a deletion): restricts
        // what actually gets packaged into the APK without touching values-bn/strings.xml
        // itself, so the translation work stays intact in the repo and re-enabling is deleting
        // this line, not redoing the translation. See docs/LOCALIZATION-UX.md §1 and
        // docs/PROGRESS.md.
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // reproducible builds (docs/DISTRIBUTION.md) come before shrinking
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        // AGP 8.7.2's bundled lint crashes analyzing this project under Kotlin 2.1.20 (bumped
        // this session, see docs/REPRODUCIBLE-BUILD.md): NonNullableMutableLiveDataDetector
        // throws IncompatibleClassChangeError against the newer Kotlin analysis-api shape, not
        // a real finding (this project doesn't use LiveData at all). Disabled per the crash's
        // own suggested workaround, not a general lint bypass -- assembleRelease's mandatory
        // lintVitalAnalyzeRelease check otherwise fails the build before packaging regardless
        // of real code issues.
        disable += "NullSafeMutableLiveData"
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
    // Bumped from 2024.12.01: Material 3 Expressive (MaterialExpressiveTheme, MaterialShapes,
    // MotionScheme) needs material3 1.4.0+, which this BOM line pulls in.
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    // Stable material3 (1.4.0 via the BOM above), deliberately not the 1.5.0-alpha that
    // carries MaterialExpressiveTheme/MaterialShapes/MotionScheme: pulling those in means
    // AGP 9.1.0 + compileSdk 37, a full toolchain migration well past a design-system pass,
    // and an alpha dependency in a project aiming for a reproducible/F-Droid build. Category
    // shapes are hand-rolled to match design/Betar Design System.dc.html's own path math
    // instead (see ui/theme/Shape.kt) rather than borrowed from MaterialShapes.
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Bottom-nav + screen routing for the real 5-destination IA (DESIGN-BRIEF.md §8), replacing
    // MainActivity's single scrolling column of every feature stacked in one screen.
    implementation("androidx.navigation:navigation-compose:2.9.7")
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

    // QR scan-to-add (DESIGN-BRIEF.md §9 screens 9-10): CameraX for the live preview + frame
    // analysis, ZXing's plain `core` artifact (pure Java, Apache 2.0, no Android Camera API of
    // its own, no Google Play Services dependency) for both decode and encode. Deliberately not
    // com.google.mlkit:barcode-scanning: that pulls in a proprietary Google model/runtime, which
    // conflicts with this project's F-Droid distribution goal (docs/DISTRIBUTION.md) the same way
    // a Play Services dependency would.
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.zxing:core:3.5.3")

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
