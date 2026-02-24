package com.lightkafka.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.lightkafka.ui.appContent

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Light Kafka Viewer",
        ) {
            appContent()
        }
    }
