pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "RudaoCloud"
// shared: all screens and data code (Compose Multiplatform); the others are thin per-platform shells.
// The iOS shell (iosApp/) is an Xcode project that links the shared framework.
include(":shared", ":app", ":desktopApp")
