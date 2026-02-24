import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.gradle.api.tasks.wrapper.Wrapper

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

val detektVersion = libs.versions.detekt.get()

subprojects {
    val isJava25OrNewer = JavaVersion.current().majorVersion.toInt() >= 25
    val detektJdkHome =
        System.getenv("SDKMAN_CANDIDATES_DIR")
            ?.let { candidatesDir ->
                file("$candidatesDir/java")
                    .takeIf { it.exists() }
                    ?.listFiles()
                    ?.firstOrNull { candidate -> candidate.isDirectory && candidate.name.startsWith("21.") }
            }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(17)
        }

        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.add("-Xjsr305=strict")
            }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extensions.configure<KtlintExtension> {
        verbose.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
    }

    extensions.configure<DetektExtension> {
        toolVersion = detektVersion
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        basePath = rootDir.absolutePath
    }

    tasks.withType<Detekt>().configureEach {
        if (isJava25OrNewer) {
            enabled = false
        }
        jvmTarget = "17"
        if (detektJdkHome != null) {
            jdkHome = detektJdkHome
        }
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
            md.required.set(false)
        }
    }
}

tasks.register("detektAll") {
    group = "verification"
    description = "Runs detekt for all subprojects."
    dependsOn(subprojects.map { project -> "${project.path}:detekt" })
}

tasks.wrapper {
    gradleVersion = libs.versions.gradle.get()
    distributionType = Wrapper.DistributionType.ALL
}
