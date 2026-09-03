import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.gradle.api.tasks.wrapper.Wrapper
import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

val detektVersion = libs.versions.detekt.get()
val ktlintVersion = "1.3.1"
val currentJvm = JavaVersion.current().majorVersion.toInt()

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "dev.detekt")
    apply(plugin = "com.diffplug.spotless")

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(currentJvm))
        }

        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(currentJvm)
            compilerOptions {
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
        toolVersion.set(detektVersion)
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        basePath.set(rootDir)
    }

    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(ktlintVersion)
                .editorConfigOverride(
                    mapOf(
                        "indent_size" to "4",
                        "continuation_indent_size" to "4",
                        "max_line_length" to "120",
                        "ktlint_code_style" to "ktlint_official",
                    ),
                )
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
                .editorConfigOverride(
                    mapOf(
                        "indent_size" to "4",
                        "continuation_indent_size" to "4",
                        "max_line_length" to "120",
                    ),
                )
        }
    }

    tasks.withType<Detekt>().configureEach {
        reports {
            html.required.set(true)
            checkstyle.required.set(true)
            sarif.required.set(true)
            markdown.required.set(false)
        }
    }
}

tasks.register("detektAll") {
    group = "verification"
    description = "Runs detekt for all subprojects."
    dependsOn(subprojects.map { project -> "${project.path}:detekt" })
}

tasks.register("spotlessCheckAll") {
    group = "verification"
    description = "Runs spotless check for all subprojects."
    dependsOn(subprojects.map { project -> "${project.path}:spotlessCheck" })
}

tasks.register("spotlessApplyAll") {
    group = "formatting"
    description = "Applies spotless formatting for all subprojects."
    dependsOn(subprojects.map { project -> "${project.path}:spotlessApply" })
}

tasks.wrapper {
    gradleVersion = libs.versions.gradle.get()
    distributionType = Wrapper.DistributionType.ALL
}
