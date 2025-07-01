pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "chronos-queue"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":transactional-commons")
include(":transactional-queue")
include(":transactional-queue-spring")
