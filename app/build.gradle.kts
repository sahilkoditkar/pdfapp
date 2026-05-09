plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.pdflens"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.pdflens"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // ABI selection lives in `splits { abi { ... } }` below — AGP rejects
        // having ndk.abiFilters overlap with the splits include list.
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("SIGNING_KEY_PATH")
                ?: project.findProperty("SIGNING_KEY_PATH") as String?
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                    ?: project.findProperty("SIGNING_STORE_PASSWORD") as String?
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                    ?: project.findProperty("SIGNING_KEY_ALIAS") as String?
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                    ?: project.findProperty("SIGNING_KEY_PASSWORD") as String?
            }
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
            // Use the release config only when a keystore is provided (CI). Locally
            // ./gradlew assembleRelease falls back to debug signing for convenience.
            signingConfig = if (System.getenv("SIGNING_KEY_PATH") != null
                || project.hasProperty("SIGNING_KEY_PATH")
            ) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Document scanning: edge detection, perspective correction, filters, and
    // PDF assembly all run on-device via Google Play Services. The actual
    // scanner UI and ML model live in Play Services, so the client library is
    // small and no images leave the device.
    implementation(libs.mlkit.document.scanner)

    debugImplementation(libs.androidx.ui.tooling)
}
