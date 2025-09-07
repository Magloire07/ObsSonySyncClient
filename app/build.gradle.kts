plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.obssonysync"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.obssonysync"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx.v1131)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // ML Kit Text Recognition
    implementation(libs.text.recognition)
    //implementation("com.google.mlkit:text-recognition:17.0.0")
    implementation(libs.play.services.mlkit.text.recognition)

    // OkHttp pour requêtes réseau
    implementation(libs.okhttp)
}
