pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}


rootProject.name = "chronos-queue"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":chronoslabs-queue-core")
include(":chronoslabs-queue-spring")
