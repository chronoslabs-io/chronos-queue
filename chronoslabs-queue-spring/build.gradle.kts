plugins {
    id("java-library")
}

dependencies {
    api(platform(libs.spring.boot.platform))
    api(libs.spring.context)
    api(libs.spring.jdbc)
    api(libs.spring.tx)
    api(projects.chronoslabsQueueCore)

    implementation(libs.logging.slf4j.api)

    testImplementation(libs.test.groovy.language)
}
