plugins {
    id("java-library")
    id("java-test-fixtures")
}

dependencies {
    api(libs.result4j)
    api(libs.observability.micrometer.core)

    implementation(libs.logging.slf4j.api)

    testImplementation(platform(libs.test.groovy.platform))
    testImplementation(libs.test.groovy.language)

    testFixturesImplementation(libs.test.assertj.core)
    testFixturesImplementation(libs.test.assertj.result4j)
}
