plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "in.sajag"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.sajag"

        // 29 is the floor the problem statement sets (Android 10+). It is also
        // the reason BouncyCastle is a hard dependency: API 29 has no platform
        // Ed25519. Do not raise this to make a crypto problem go away — the
        // workers this is for are on 10 and 11.
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // arm64 only. Halves the install size against a universal APK, and
        // every device in scope has been arm64 for years. See the budget in
        // the root README: base APK ceiling is 40 MB.
        ndk { abiFilters += listOf("arm64-v8a") }

        // Where the sync worker pushes sealed bundles. Override per build:
        //   gradlew assembleWorkerDebug -PsajagServer=http://192.168.1.20:8000
        val server = (project.findProperty("sajagServer") as String?) ?: "http://10.0.2.2:8000"
        buildConfigField("String", "SERVER_URL", "\"$server\"")

        // The training centre's issuer public key (64 hex characters) that a
        // RELEASE build trusts. Debug builds trust the published development
        // key instead. Leave empty and a release verifier trusts only its own
        // device key:
        //   gradlew assembleWorkerRelease -PsajagIssuerPublicKey=<64 hex>
        val issuerKey = (project.findProperty("sajagIssuerPublicKey") as String?)?.trim() ?: ""
        buildConfigField("String", "ISSUER_PUBLIC_KEY_HEX", "\"$issuerKey\"")

        // Room exports its schema so migrations are reviewable in a diff
        // rather than discovered on a worker's phone.
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    // One source of truth for module content: the app ships the exact scenario
    // files the Python engine and the server score against.
    sourceSets["main"].assets.srcDir("../../core/scenarios")

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
                          "proguard-rules.pro")
        }
        debug { applicationIdSuffix = ".debug" }
    }

    // The standalone verifier an inspector carries. Same codebase; once Unity
    // lands it ships only in the worker flavour, which is how the verifier
    // stays small enough to install on a gate guard's phone without an argument.
    flavorDimensions += "role"
    productFlavors {
        create("worker") {
            dimension = "role"
        }
        create("verifier") {
            dimension = "role"
            applicationIdSuffix = ".verifier"
            versionNameSuffix = "-verifier"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    // Filament reads its compiled materials straight from the APK; stored
    // uncompressed they load without an extra copy.
    androidResources {
        noCompress.addAll(listOf("filamat", "ktx"))
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // The codec vector test is pure JVM logic — no device, no emulator.
            // See CodecVectorTest.kt.
            all { it.testLogging { events("passed", "failed", "skipped") } }
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)

    implementation(libs.bouncycastle)
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)

    // Camera view (hazards drawn over the live camera) and the ARCore probe.
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.arcore)

    // World-anchored AR: ARCore tracking and Filament rendering through
    // SceneView. The drill falls back to the camera view on any phone where
    // ARCore is missing or fails, so this never blocks a drill.
    implementation(libs.arsceneview)

    // Unity as a library, once the editor has exported it. See settings.gradle.kts.
    // "workerImplementation"(project(":unityLibrary"))

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
}

/**
 * Fail the build if the base APK outgrows its budget.
 *
 * "Runs on mid-range" is not a claim you can make in December unless the build
 * has been failing over it since October. This is that gate. Raise the ceiling
 * deliberately, in a commit someone can argue with — never by accident.
 */
val apkBudgetMb = 40
tasks.register("checkApkBudget") {
    group = "verification"
    description = "Fails if the release APK exceeds ${apkBudgetMb} MB."
    doLast {
        val apks = fileTree("${layout.buildDirectory.get().asFile}/outputs/apk/worker/release") { include("*.apk") }
        apks.forEach { apk ->
            val mb = apk.length() / (1024.0 * 1024.0)
            println("APK ${apk.name}: %.1f MB".format(mb))
            if (mb > apkBudgetMb) {
                throw GradleException(
                    "%s is %.1f MB, over the %d MB budget. ".format(apk.name, mb, apkBudgetMb) +
                    "Move assets into a per-module pack before raising this."
                )
            }
        }
    }
}
