import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.eaglelan.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eaglelan.app"
        minSdk = 26
        targetSdk = 35
        // CI passes -PversionCode / -PversionName (see .github/workflows/release.yml).
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.1.0-dev"
    }

    signingConfigs {
        // Releases are signed with the same key as local debug builds so they install over them.
        // CI provides the keystore via SIGNING_KEYSTORE; locally the default debug keystore is used.
        create("release") {
            val ci = System.getenv("SIGNING_KEYSTORE")
            if (ci != null) {
                storeFile = file(ci)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "android"
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // AGP 8.7's lint can't read Kotlin 2.2 metadata yet; it only runs on demand.
    lint { checkReleaseBuilds = false }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
}
