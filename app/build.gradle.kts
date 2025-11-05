import org.gradle.kotlin.dsl.androidTestImplementation
import java.util.Properties
import java.io.FileReader

fun getLocalProperty(key: String): String {
    val properties = Properties()
    val localPropertiesFile = project.rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        properties.load(localPropertiesFile.reader())
    }
    return properties.getProperty(key) ?: ""
}
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization")

}

android {
    namespace = "com.cehpoint.netwin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cehpoint.netwin"
        minSdk = 28
        targetSdkVersion(rootProject.extra["defaultTargetSdkVersion"] as Int)
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "GEMINI_API_KEY", "\"${getLocalProperty("GEMINI_API_KEY")}\"")
    }

    buildTypes {
        debug {
            // Debug configuration
            buildConfigField("boolean", "TOURNAMENT_UI_V2", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Default OFF in release for safe rollout
            buildConfigField("boolean", "TOURNAMENT_UI_V2", "false")
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            // ⭐️ FIX: Updated excludes list to handle META-INF/LICENSE-notice.md and other common conflicts
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/*.txt",
                "META-INF/*.version"
            )
        }
    }
    buildToolsVersion = rootProject.extra["buildToolsVersion1"] as String
}

// Add Room schema directory configuration


dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material:material:1.6.8") // Modern stable Material 1
    implementation("androidx.navigation:navigation-compose:2.9.5")


    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.57.1")
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage.ktx)
    implementation("com.google.firebase:firebase-appcheck-playintegrity:19.0.1")
    implementation("com.google.firebase:firebase-appcheck-debug:19.0.1")
    implementation(libs.androidx.compose.foundation)
    implementation(libs.generativeai)
    implementation(libs.androidx.room.ktx)
    ksp("com.google.dagger:hilt-android-compiler:2.57.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Coroutines (Updated to 1.8.1 for consistency)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Datastore preference
    implementation ("androidx.datastore:datastore-preferences:1.1.7")

    // Payment Gateways (Phase 2)
    implementation("com.razorpay:checkout:1.6.41")
    implementation("co.paystack.android:paystack:3.1.3")

    // -------------------------------------------------------------------------
    // UNIT TESTING
    // -------------------------------------------------------------------------
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1") // Consistent version

    // -------------------------------------------------------------------------
    // ANDROID INSTRUMENTATION TESTING
    // -------------------------------------------------------------------------
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Hilt testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.57.1")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.57.1")

    // Navigation Testing
    androidTestImplementation("androidx.navigation:navigation-testing:2.9.5")

    // UI Automator
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    // Mocking for Instrumented Tests
    androidTestImplementation("org.mockito:mockito-android:4.11.0")
    androidTestImplementation("io.mockk:mockk-android:1.13.4")
    androidTestImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")

    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1") // Consistent version

    // Truth assertions
    androidTestImplementation("com.google.truth:truth:1.1.3")

    // Other app dependencies
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("javax.inject:javax.inject:1")
    implementation("com.google.accompanist:accompanist-swiperefresh:0.36.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.ai.client.generativeai:generativeai:0.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    // CameraX core library using the camera2 implementation
    val camerax_version = "1.5.0-alpha01"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
    implementation(project(":core:utils"))
}