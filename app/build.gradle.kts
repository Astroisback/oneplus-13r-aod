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
    // X509 helper the library asks for on Android.
    implementation("com.github.MuntashirAkon:sun-security-android:1.1")
    // A Conscrypt build that works when talking to a REMOTE adbd - the pair here
    // is to 127.0.0.1 but the TLS stack is the same code path.
    implementation("org.conscrypt:conscrypt-android:2.5.3")
}
