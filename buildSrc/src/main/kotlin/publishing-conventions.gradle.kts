plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("sonatype") {
            groupId = "io.chronoslabs.queue"
            from(components["java"])

            pom {
                name = project.name
                description = "Chronos Queue module: ${project.name}"
                url = "https://github.com/chronoslabs-io/chronos-queue"
                inceptionYear = "2025"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "dkubicki"
                        name = "Dawid Kubicki"
                    }
                    developer {
                        id = "marcindabrowski"
                        name = "Marcin Dabrowski"
                    }
                }
                scm {
                    connection = "scm:git@github.com:chronoslabs-io/chronos-queue.git"
                    developerConnection = "scm:git@github.com:chronoslabs-io/chronos-queue.git"
                    url = "https://github.com/chronoslabs-io/chronos-queue"
                }
            }
        }
    }
}

signing {
    setRequired {
        System.getenv("GPG_KEY_ID") != null
    }
    useInMemoryPgpKeys(
        System.getenv("GPG_KEY_ID"),
        System.getenv("GPG_PRIVATE_KEY"),
        System.getenv("GPG_PRIVATE_KEY_PASSWORD")
    )
    sign(publishing.publications["sonatype"])
}
