plugins {
    alias(libs.plugins.android.application)
    // AGP 9.x has built-in Kotlin support — no separate kotlin("android") plugin needed.
    // If you need to opt out for any reason, add android.builtInKotlin=false to gradle.properties.
    id("com.google.gms.google-services")  // Firebase Google Services plugin
}

android {
    namespace = "com.sapotlokal"
    compileSdk = 37  // Latest API level supported by AGP 9.4.0

    defaultConfig {
        applicationId = "com.sapotlokal"
        minSdk = 26           // Android 8.0 — as required by the build guide
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // JDK 21 matches Android Studio's bundled JDK.
        // AGP 9.x minimum is JDK 17 — 21 is fully compatible and is what ships with Android Studio.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Kotlin source sets — AGP 9.x auto-configures Kotlin; this just points to the right folder.
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/kotlin")
        }
    }

    buildFeatures {
        viewBinding = true   // Replaces findViewById — safer and cleaner for XML layouts
    }
}

dependencies {
    // ── AndroidX ─────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)

    // ── Material Design ───────────────────────────────────────────────────────
    implementation(libs.material)

    // ── Firebase (BoM manages all Firebase library versions) ──────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)

    // ── QR Scanning — ML Kit + CameraX ────────────────────────────────────────
    // ML Kit barcode-scanning does the recognition; CameraX feeds it the frames.
    // CameraX handles camera lifecycle, rotation, and permission automatically.
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)       // PreviewView widget used in the scan layout

    // ── QR Generation — ZXing core ────────────────────────────────────────────
    // Used ONLY to draw QR Bitmaps into an ImageView. No camera access.
    // The deprecated zxing-android-embedded scanner is NOT included.
    implementation(libs.zxing.core)

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
