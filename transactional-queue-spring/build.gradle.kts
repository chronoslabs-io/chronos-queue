plugins {
    id("java-library")
}

dependencies {
    api(platform(libs.spring.boot.platform))
    api(libs.spring.context)
    api(libs.spring.jdbc)
    api(libs.spring.tx)
    api(projects.transactionalCommons)
    api(projects.transactionalQueue)

    implementation(projects.transactionalCommons)
    implementation(libs.logging.slf4j.api)
    implementation(libs.observability.micrometer.core)

    testImplementation(libs.test.groovy.language)
}
