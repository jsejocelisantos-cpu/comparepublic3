plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.compare"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.compare"
        minSdk = 26
        targetSdk = 36 // Deve acompanhar o compileSdk
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

    implementation("com.google.guava:guava:31.1-android")

    // --- CORREÇÃO IMPORTANTE: Futures (Necessário para o CameraX) ---
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")

    // --- CÂMERA (CameraX) - Versão unificada para 1.5.2 ---
    val cameraxVersion = "1.5.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // --- INTEGRAÇÃO COM IA (Gemini) ---
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // --- FIREBASE (Banco de Dados) ---
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-firestore")

    // --- SCANNER DE CÓDIGO DE BARRAS (Google ML Kit) ---
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // --- UI E NAVEGAÇÃO ---
    // Ícones extendidos (Material Design - Lupa, etc)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Activity e Compose Basics
    implementation("androidx.activity:activity-compose:1.10.0") // Versão ajustada para compatibilidade
    // Nota: androidx.compose.ui:ui já é puxado pelo BOM abaixo, removi para evitar duplicidade explícita

    // --- DEPENDÊNCIAS DO CATÁLOGO (LIBS) ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // BOM (Bill of Materials) - Gerencia as versões do Compose
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation.layout)

    // --- TESTES ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}