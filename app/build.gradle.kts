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
    // (SPAKE2) and the daily connect. Small, single-purpose, widely used.
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    // BouncyCastle for generating the client X.509 that libadb-android needs.
    // Chosen over sun-security-android because JitPack often fails to build the
    // latter on first request, and BC is on Maven Central so the build is stable.
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    // Conscrypt build compatible with talking to a remote adbd. The pair here is
    // 127.0.0.1 but the TLS code path is the same.
    implementation("org.conscrypt:conscrypt-android:2.5.3")
}
