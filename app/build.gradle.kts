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

        ndk {
            // OpenCV's native libs dominate APK size (~30-50 MB per ABI). Ship only
            // arm64-v8a — it covers virtually every active Android phone since 2019.
            // Re-add "armeabi-v7a" if you need to support legacy 32-bit devices, and
            // "x86_64" only if you publish to emulator users / Chromebooks. Prefer
            // ABI splits below over a fat APK that bundles every architecture.
            abiFilters += listOf("arm64-v8a")
        }
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
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // Ship one APK per ABI rather than a fat APK. Drops install size dramatically
    // for users on a single architecture. Disable if you only distribute via Play
    // (App Bundle handles per-device delivery automatically — see README).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
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

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.documentfile)
    implementation(libs.opencv)

    debugImplementation(libs.androidx.ui.tooling)
}
