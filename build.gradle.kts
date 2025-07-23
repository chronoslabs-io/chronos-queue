import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import pl.allegro.tech.build.axion.release.domain.PredefinedVersionCreator
import java.time.Instant

plugins {
    id("codenarc")
    id("jacoco")
    id("java")
    id("java-library")
    id("maven-publish")
    id("pmd")
    alias(libs.plugins.release)
    alias(libs.plugins.conventionalCommits)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.spotless)
    alias(libs.plugins.testLogger)
    signing
}

scmVersion {
    versionCreator = PredefinedVersionCreator.VERSION_WITH_BRANCH.versionCreator
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

    group = "com.tink.log"
    version = scmVersion.version

    repositories {
        mavenCentral()
    }

    java {
        withJavadocJar()
        withSourcesJar()
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
            create<MavenPublication>("tink-java-logging") {
                from(components["java"])

                pom {
                    url = "https://github.com/chronoslabs-io/chronos-queue"

                    scm {
                        connection = "scm:git:git@github.com:chronoslabs-io/chronos-queue"
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
            targetExclude("build/generated/")
        }
    }

    tasks.withType<JacocoReport> {
        executionData = fileTree("${project.layout.buildDirectory.get()}/jacoco/")
        reports {
            csv.required = false
            html.required = true
            xml.required = true
        }
    }

    tasks.withType<JavaCompile> {
        options.errorprone {
            disableWarningsInGeneratedCode = true
            excludedPaths = ".*/(build|src)/generated/.*"
        }
        options.isFork = true
    }

    tasks.withType<Jar> {
        manifest {
            attributes(
                "Build-Jdk"  to "${System.getProperty("java.version")} (${System.getProperty("java.vendor")} ${System.getProperty("java.vm.version")})",
                "Build-Jdk-Spec"         to System.getProperty("java.specification.version"),
                "Build-OS"               to "${System.getProperty("os.name")} ${System.getProperty("os.arch")} ${System.getProperty("os.version")}",
                "Build-Timestamp"        to Instant.now().toString(),
                "Created-By"             to "Gradle ${gradle.gradleVersion}",
                "Implementation-Title"   to project.name,
                "Implementation-URL"     to "https://github.com/chronoslabs-io/chronos-queue",
                "Implementation-Vendor"  to "Chronoslabs",
                "Implementation-Version" to project.version,
                "X-Compile-Source-JDK"   to project.extensions.getByType<JavaPluginExtension>().sourceCompatibility.toString(),
                "X-Compile-Target-JDK"   to project.extensions.getByType<JavaPluginExtension>().targetCompatibility.toString(),
            )
        }
    }

    tasks.withType<Pmd> {
        reports {
            xml.required = true
            html.required = true
        }
    }

    tasks.withType<Test> {
        finalizedBy(tasks.jacocoTestReport)
        jvmArgs("-XX:+EnableDynamicAgentLoading")
        maxParallelForks = 1

        useJUnitPlatform()

        testLogging {
            exceptionFormat = FULL
            events = setOf(FAILED, PASSED, SKIPPED, STANDARD_ERROR)
        }

        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent != null) {
                    if (result.testCount.toInt() == 0) {
                        throw IllegalStateException("No tests were found. Failing the build")
                    }
                }
            }
        })
    }
}

@Suppress("UnstableApiUsage")
tasks.wrapper {
    networkTimeout = 20000
}
