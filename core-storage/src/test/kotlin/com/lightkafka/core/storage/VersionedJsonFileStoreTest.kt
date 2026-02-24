package com.lightkafka.core.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class VersionedJsonFileStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `migrates payload between schema versions`() {
        val file = tempDir.resolve("data.json")
        file.writeText("""{"version":1,"payload":{"text":"hello"}}""")

        val store =
            VersionedJsonFileStore(
                file = file,
                serializer = MigrationTarget.serializer(),
                currentVersion = 2,
                migrations =
                    mapOf(
                        1 to { payload ->
                            val obj = payload as JsonObject
                            buildJsonObject {
                                put("message", JsonPrimitive(obj["text"]?.jsonPrimitive?.content ?: ""))
                            }
                        },
                    ),
            )

        val loaded = store.load(defaultValue = MigrationTarget(message = "default"))

        assertEquals(MigrationTarget(message = "hello"), loaded)
    }

    @Serializable
    private data class MigrationTarget(val message: String)
}
