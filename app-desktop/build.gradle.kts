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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            modules(
                "java.base",
                "java.desktop",
                "java.net.http",
                "jdk.unsupported",
            )
        }
    }
}
