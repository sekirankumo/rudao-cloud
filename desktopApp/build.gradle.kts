import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
}

// jpackage wants a plain MAJOR.MINOR.BUILD version.
val appVersion = ((findProperty("versionName") as String?) ?: "0.1.0").substringBefore('-')

compose.desktop {
    application {
        mainClass = "com.eaglelan.app.desktop.MainKt"
        // Shrinking saves little next to the bundled JRE and libVLC, and trips over Ktor/vlcj reflection.
        buildTypes.release.proguard { isEnabled.set(false) }
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "RudaoCloud"
            packageVersion = appVersion
            description = "入到云盘"
            vendor = "sekirankumo"
            // libVLC (downloaded by downloadVlc) ships next to the app, see Vlc in the shared module.
            appResourcesRootDir.set(layout.projectDirectory.dir("resources"))
            modules("java.sql", "java.prefs", "java.naming", "jdk.unsupported", "jdk.crypto.ec", "java.net.http")
            windows {
                iconFile.set(layout.projectDirectory.file("icons/app.ico"))
                menu = true
                menuGroup = "入到云盘"
                shortcut = true
                dirChooser = false
                perUserInstall = true
                // Never change: lets a new installer replace the installed version.
                upgradeUuid = "6f0e2a4c-9d3b-4b8e-a1f7-2c5d8e9b3a41"
            }
        }
    }
}

// ---- libVLC: the Windows player (VideoLAN's official build, verified against its published SHA-256) ----
val vlcVersion = "3.0.21"
val vlcDir = layout.projectDirectory.dir("resources/windows/vlc")
// Not needed for playing local-network mp4/mov; leaving them out keeps the installer smaller.
val vlcUnusedPlugins = listOf("gui", "lua", "visualization", "video_splitter", "stream_out", "services_discovery", "control", "mux", "keystore", "spu")

val downloadVlc by tasks.registering {
    val zipFile = layout.buildDirectory.file("vlc/vlc-$vlcVersion-win64.zip")
    outputs.dir(vlcDir)
    onlyIf { !vlcDir.file("libvlc.dll").asFile.exists() }
    doLast {
        val url = "https://download.videolan.org/pub/videolan/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip"
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
        val zip = zipFile.get().asFile.apply { parentFile.mkdirs() }
        if (!zip.exists()) {
            client.send(HttpRequest.newBuilder(URI(url)).build(), HttpResponse.BodyHandlers.ofFile(zip.toPath()))
        }
        val expected = client.send(HttpRequest.newBuilder(URI("$url.sha256")).build(), HttpResponse.BodyHandlers.ofString())
            .body().trim().substringBefore(' ').lowercase()
        val actual = MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
        if (expected.length != 64 || expected != actual) {
            zip.delete()
            throw GradleException("VLC download failed verification (expected $expected, got $actual)")
        }
        val root = "vlc-$vlcVersion/"
        copy {
            from(zipTree(zip))
            include("${root}libvlc.dll", "${root}libvlccore.dll", "${root}plugins/**")
            vlcUnusedPlugins.forEach { exclude("${root}plugins/$it/**") }
            exclude("${root}plugins/plugins.dat")
            eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
            includeEmptyDirs = false
            into(vlcDir)
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(downloadVlc) }
