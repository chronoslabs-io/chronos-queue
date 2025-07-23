import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import java.time.Instant

plugins {
    id("codenarc")
    id("jacoco")
    id("java")
    id("java-library")
    id("maven-publish")
    id("pmd")
    id("signing")
    alias(libs.plugins.conventionalCommits)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.release)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.spotless)
    alias(libs.plugins.testLogger)
}

subprojects {

    if (this.childProjects.isNotEmpty()) {
        return@subprojects
    }
    apply(plugin = "codenarc")
    apply(plugin = "com.adarshr.test-logger")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "groovy")
    apply(plugin = "jacoco")
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "net.ltgt.errorprone")
    apply(plugin = "pl.allegro.tech.build.axion-release")
    apply(plugin = "pmd")
    apply(plugin = "signing")

    group = "io.chronoslabs.queue"
    version = scmVersion.version

    repositories {
        mavenCentral()
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencies {
        errorprone(rootProject.libs.dev.errorprone.core)

        testImplementation(platform(rootProject.libs.test.groovy.platform))
        testImplementation(platform(rootProject.libs.test.junit.platform))
        testImplementation(platform(rootProject.libs.spring.boot.platform))

        testImplementation(rootProject.libs.test.archunit)
        testImplementation(rootProject.libs.test.groovy.json)
        testImplementation(rootProject.libs.test.junit.jupiter.api)
        testImplementation(rootProject.libs.test.spock.core)
        testImplementation(rootProject.libs.test.spock.reports)
        testImplementation(rootProject.libs.test.spring.boot.starter)

        testRuntimeOnly(rootProject.libs.test.junit.jupiter.engine)
        testRuntimeOnly(rootProject.libs.test.junit.platformLauncher)
    }

    codenarc {
        configFile = rootProject.file("gradle/config/codenarc/CodenarcRuleSet.groovy")
        reportFormat = "console"
        toolVersion = rootProject.libs.versions.dev.codenarc.get()
    }

    jacoco {
        toolVersion = rootProject.libs.versions.dev.jacoco.get()
    }

    pmd {
        isConsoleOutput = true
        isIgnoreFailures = false
        ruleSets = listOf(
            "category/java/bestpractices.xml",
            "category/java/errorprone.xml",
            "category/java/performance.xml",
            "category/java/security.xml",
        )
        toolVersion = rootProject.libs.versions.dev.pmd.get()
    }

    publishing {
        publications {
            create<MavenPublication>("chronoslabs-queue") {
                from(components["java"])

                pom {
                    url = "https://github.com/chronoslabs-io/chronos-queue"

                    scm {
                        connection = "scm:git:git@github.com:chronoslabs-io/chronos-queue.git"
                        developerConnection = "scm:git:git@github.com:chronoslabs-io/chronos-queue.git"
                        url = "https://github.com/chronoslabs-io/chronos-queue"
                    }
                }
            }
        }
        repositories {
            maven {
                name = "MavenCentral"
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = System.getenv("SONATYPE_USERNAME")
                    password = System.getenv("SONATYPE_PASSWORD")
                }
            }
        }
    }

    signing {
        useInMemoryPgpKeys(
            System.getenv("GPG_KEY_ID"),
            System.getenv("GPG_PRIVATE_KEY"),
            System.getenv("GPG_PRIVATE_KEY_PASSWORD")
        )
        sign(publishing.publications)
    }

    spotless {
        java {
            googleJavaFormat(rootProject.libs.versions.dev.googleJavaFormat.get()).reflowLongStrings()
        }
    }

    tasks.withType<JacocoReport>().configureEach {
        executionData = fileTree("${project.layout.buildDirectory.get()}/jacoco/")
        reports {
            csv.required = false
            html.required = true
            xml.required = true
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.errorprone {
            disableWarningsInGeneratedCode = true
            excludedPaths = ".*/(build|src)/generated/.*"
        }
        options.isFork = true
        options.release.set(17)
    }

    tasks.withType<Jar>().configureEach {
        val jdkLauncherMetadata = javaToolchains.launcherFor {
            implementation.set(java.toolchain.implementation.get())
            languageVersion.set(java.toolchain.languageVersion.get())
            vendor.set(java.toolchain.vendor.get())
        }.get().metadata
        val javaCompileTask = tasks.withType<JavaCompile>().getByName("compileJava")
        val releaseJavaVersion = javaCompileTask.options.release.getOrElse(java.toolchain.languageVersion.get().asInt())
        val projectVersion = org.gradle.util.internal.VersionNumber.parse(project.version.toString())

        manifest {
            attributes(
                "Build-Jdk"              to "${jdkLauncherMetadata.jvmVersion} (${jdkLauncherMetadata.vendor})",
                "Build-Jdk-Spec"         to releaseJavaVersion,
                "Build-OS"               to "${System.getProperty("os.name")} ${System.getProperty("os.arch")} ${System.getProperty("os.version")}",
                "Build-Timestamp"        to Instant.now().toString(),
                "Created-By"             to "Gradle ${gradle.gradleVersion}",
                "Implementation-Title"   to project.name,
                "Implementation-URL"     to "https://github.com/chronoslabs-io/chronos-queue",
                "Implementation-Vendor"  to "Chronoslabs",
                "Implementation-Version" to project.version,
                "Specification-Title"    to project.name,
                "Specification-Vendor"   to "Chronoslabs",
                "Specification-Version"  to "${projectVersion.major}.${projectVersion.minor}",
            )
        }
    }

    tasks.withType<Pmd>().configureEach {
        reports {
            xml.required = true
            html.required = true
        }
    }

    tasks.withType<Test>().configureEach {
        finalizedBy(tasks.jacocoTestReport)
        jvmArgs("-XX:+EnableDynamicAgentLoading")
        maxParallelForks = 1

        useJUnitPlatform()

        testLogging {
            exceptionFormat = FULL
            events = setOf(FAILED, PASSED, SKIPPED, STANDARD_ERROR)
        }
    }
}
