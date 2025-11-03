plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.streamchat" // keeping namespace to avoid R import churn
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.temp"
        minSdk = 26
        targetSdk = 35
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // Stream Chat SDK - Core Libraries
    implementation(libs.stream.chat.client)
    implementation(libs.stream.chat.state)
    implementation(libs.stream.chat.offline)

    // Stream Chat SDK - UI Components
    implementation(libs.stream.chat.ui.components)
    implementation(libs.stream.chat.compose)

    // Compose
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.3")
    implementation("androidx.compose.ui:ui:1.6.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.3")
    implementation("androidx.compose.runtime:runtime:1.6.3")
    implementation("androidx.compose.runtime:runtime-livedata:1.6.3")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    // LiveData (Flow <-> LiveData bridges)
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Core Android
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // OkHttp for backend token requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Firebase Auth
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")
}