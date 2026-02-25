import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(project(":core-kafka"))
    implementation(project(":core-storage"))
    implementation(project(":ui"))

    implementation(compose.desktop.currentOs)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

compose.desktop {
    application {
        mainClass = "com.lightkafka.app.MainKt"

        nativeDistributions {
            packageName = "LightKafkaViewer"
            packageVersion = "1.0.0"
            val osName = System.getProperty("os.name").lowercase()
            val currentOsFormat =
                when {
                    osName.contains("mac") -> TargetFormat.Dmg
                    osName.contains("win") -> TargetFormat.Msi
                    else -> TargetFormat.AppImage
                }
            targetFormats(currentOsFormat)

            modules(
                "java.base",
                "java.desktop",
                "java.net.http",
                "jdk.unsupported",
            )
        }
    }
}
