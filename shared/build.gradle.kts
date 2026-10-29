import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// One version for every platform; CI passes -PversionName (see .github/workflows/release.yml).
val appVersion = (findProperty("versionName") as String?) ?: "0.1.0-dev"

val generateBuildInfo by tasks.registering {
    val out = layout.buildDirectory.dir("generated/buildinfo")
    val version = appVersion
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val file = out.get().file("com/eaglelan/app/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package com.eaglelan.app\n\nconst val APP_VERSION = \"$version\"\n")
    }
}

val ktor = "3.2.3"
val room = "2.7.2"

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildInfo)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
                implementation("androidx.room:room-runtime:$room")
                implementation("androidx.sqlite:sqlite-bundled:2.5.2")
                implementation("io.ktor:ktor-client-core:$ktor")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("io.coil-kt.coil3:coil-compose:3.3.0")
                implementation("io.coil-kt.coil3:coil-network-ktor3:3.3.0")
                implementation("com.russhwolf:multiplatform-settings:1.3.0")
            }
        }
        // Android and desktop are both JVM: they share the OkHttp engine and the update downloader.
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:$ktor")
            }
        }
        androidMain {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation("androidx.core:core-ktx:1.15.0")
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.media3:media3-exoplayer:1.5.1")
                implementation("androidx.media3:media3-ui:1.5.1")
                implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
            }
        }
        val desktopMain by getting {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(compose.desktop.common)
                implementation("uk.co.caprica:vlcj:4.8.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
            }
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktor")
        }
    }
}

android {
    namespace = "com.eaglelan.app.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    // AGP 8.7's lint can't read Kotlin 2.2 metadata yet; it only runs on demand.
    lint { checkReleaseBuilds = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    listOf("kspAndroid", "kspDesktop", "kspIosArm64", "kspIosSimulatorArm64").forEach {
        add(it, "androidx.room:room-compiler:$room")
    }
}

compose.resources {
    packageOfResClass = "com.eaglelan.app.resources"
}

// KSP (Room) reads every source dir, including the generated resource accessors and BuildInfo.
tasks.matching { it.name.startsWith("ksp") }.configureEach {
    dependsOn(generateBuildInfo)
    dependsOn(tasks.matching { t ->
        t.name == "generateComposeResClass" || (t.name.startsWith("generate") && t.name.contains("Resource"))
    })
}
