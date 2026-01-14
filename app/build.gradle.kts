plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.compare"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.compare"
        minSdk = 26
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // SDK do Gemini para Android
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")


    // camera
    val cameraxVersion = "1.5.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")


        // FIREBASE (Banco de Dados)
        implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
        implementation("com.google.firebase:firebase-firestore")

        // ÍCONES EXTENDIDOS (Para ícones específicos do Material Design)
        implementation("androidx.compose.material:material-icons-extended:1.7.8")

        // NAVIGATON E ACTIVITY (Básico)
        implementation("androidx.activity:activity-compose:1.12.2")
        implementation("androidx.compose.ui:ui:1.10.0")


    // Scanner de Código de Barras (Google)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Ícones extras (Lupa, etc)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation.layout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // MANTENHA ESTAS (o BOM já cuida da versão correta):
    implementation(platform(libs.androidx.compose.bom))

}