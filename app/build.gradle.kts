plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astro.displayhelper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.astro.displayhelper"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Off-the-UI-thread work without a persistent background service that would show
    // up in a package-list scan.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Muntashir's libadb-android handles the Android-11+ wireless-adb TLS pair
    // (SPAKE2) and the daily connect. Pinned to the latest master commit, as
    // OxygenOS 17 changed something in adbd's handshake and 3.1.1 (the last
    // release tag) started failing. The commit below is from Jan 2026.
    implementation("com.github.MuntashirAkon:libadb-android:c849886ebc")
    // Must match the version libadb's internal spake2-android+core expect.
    // Duplicate-class check in AGP will blow up if these drift apart.
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    // Latest stable conscrypt (2.7.0, released 2026-08-31). The 2.6/2.7 series
    // adds TLS-stack updates that the adbd on OxygenOS 17 appears to need; the
    // old 2.5.3 line (Aug 2024) stopped working after that OTA.
    implementation("org.conscrypt:conscrypt-android:2.7.0")
}
