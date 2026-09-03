package com.lightkafka.core.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class VersionedJsonFileStore<T>(
    private val file: Path,
    private val serializer: KSerializer<T>,
    private val currentVersion: Int,
    private val migrations: Map<Int, (JsonElement) -> JsonElement> = emptyMap(),
    private val json: Json = defaultJson,
) {
    fun load(defaultValue: T): T {
        if (!file.exists()) {
            return defaultValue
        }

        val parsedRoot = json.parseToJsonElement(file.readText()).jsonObject
        val (startVersion, startPayload) = envelopeFrom(parsedRoot)

        if (startVersion > currentVersion) {
            error(
                "Storage version $startVersion is newer than supported version $currentVersion",
            )
        }

        var version = startVersion
        var payload = startPayload

        while (version < currentVersion) {
            val migration =
                migrations[version]
                    ?: error("Missing migration from version $version to ${version + 1}")
            payload = migration(payload)
            version += 1
        }

        return json.decodeFromJsonElement(serializer, payload)
    }

    fun save(value: T) {
        file.parent?.createDirectories()
        val payload = json.encodeToJsonElement(serializer, value)
        val envelope =
            buildJsonObject {
                put("version", JsonPrimitive(currentVersion))
                put("payload", payload)
            }

        file.writeText(json.encodeToString(JsonObject.serializer(), envelope))
    }

    private fun envelopeFrom(root: JsonObject): Pair<Int, JsonElement> {
        val payload = root["payload"]
        val version = root["version"]?.jsonPrimitive?.intOrNull

        if (payload != null && version != null) {
            return version to payload
        }

        return 0 to root
    }

    private companion object {
        val defaultJson =
            Json {
                prettyPrint = true
                encodeDefaults = true
                ignoreUnknownKeys = true
            }
    }
}
