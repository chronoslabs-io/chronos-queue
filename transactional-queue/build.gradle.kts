plugins {
    id("java-library")
    id("java-test-fixtures")
}

dependencies {
    api(libs.result4j)

    implementation(projects.transactionalCommons)
    implementation(libs.logging.slf4j.api)
    implementation(libs.observability.micrometer.coreVersioned)

    testImplementation(libs.test.groovy.language)

    testFixturesImplementation(platform(libs.test.groovy.platform))
    testFixturesImplementation(libs.observability.micrometer.coreVersioned)
    testFixturesImplementation(libs.test.assertj.core)
    testFixturesImplementation(libs.test.assertj.result4j)
    testFixturesImplementation(libs.test.groovy.language)
}
