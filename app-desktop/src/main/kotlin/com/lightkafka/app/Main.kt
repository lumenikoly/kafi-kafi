package com.lightkafka.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lightkafka.ui.appContent
import java.awt.Dimension

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
            title = "Kafi Kafi",
        ) {
            window.minimumSize = Dimension(1024, 680)
            appContent()
        }
    }
