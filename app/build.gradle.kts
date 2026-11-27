plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.retouche.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.retouche.app.pro"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Téléphones réels uniquement (allège l'APK de la bibliothèque ONNX).
        // Pour tester sur l'émulateur PC, ajoute "x86_64" à cette liste.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Usage personnel : signe avec la clé de debug pour obtenir un APK installable
            // directement. Pour publier (F-Droid, etc.), remplace par ta propre clé.
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
    }

    // Le modèle IA ne doit pas être compressé dans l'APK.
    androidResources {
        noCompress += "onnx"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Lecture de l'orientation EXIF des photos
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // IA de détourage 100 % locale
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.32.0")
}
