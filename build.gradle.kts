import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    java
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs)
}

group = project.property("group").toString()
version = project.property("version").toString()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(22))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // compileOnly
    compileOnly(libs.montoya)
    compileOnly(libs.logbackClassic)
    compileOnly(libs.spotbugsAnnotations)

    // implementation
    implementation(libs.miglayoutSwing)

    // testImplementation
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(libs.mockitoCore)
    testImplementation(libs.mockitoJunitJupiter)
    testImplementation(libs.assertjCore)
    testImplementation(libs.montoya)

    // testRuntimeOnly
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)

    // runtimeOnly
    runtimeOnly(libs.logbackClassic)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:deprecation",
            "-Xlint:unchecked",
            "-Xlint:cast",
            "-Xlint:rawtypes",
        ),
    )
}

spotbugs {
    toolVersion.set(libs.versions.spotbugs.get())
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.HIGH)
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") {
        required.set(true)
    }
}

spotless {
    java {
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.test {
    useJUnitPlatform()
    workingDir = project.projectDir
    systemProperty("java.awt.headless", "true")

    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) {
                logger.lifecycle(
                    "Test summary: total=${result.testCount}, passed=${result.successfulTestCount}, "
                            + "failed=${result.failedTestCount}, skipped=${result.skippedTestCount}",
                )
            }
        }

        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            if (result.resultType == TestResult.ResultType.FAILURE) {
                logger.lifecycle("FAILED ${testDescriptor.className} > ${testDescriptor.name}")
            }
        }
    })
}

tasks.named<ProcessResources>("processResources") {
    from(rootProject.layout.projectDirectory.files("README.md", "LICENSE")) {
        into("")
    }
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a fat JAR containing compiled classes and runtime dependencies for Burp."
    val base = (findProperty("archivesBaseName") as String?) ?: project.name
    archiveBaseName.set(base)
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.assemble {
    dependsOn("fatJar")
}

tasks.build {
    dependsOn("fatJar")
}
