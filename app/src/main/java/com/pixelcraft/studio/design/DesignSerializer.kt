package com.pixelcraft.studio.design

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What is written to disk for one project (images are stored separately). */
@Serializable
data class DesignProjectFile(
    val version: Int = 1,
    val name: String,
    val updatedAtEpochMs: Long,
    val document: DesignDocument
)

object DesignSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(file: DesignProjectFile): String =
        json.encodeToString(DesignProjectFile.serializer(), file)

    /** Returns a failure for corrupt or invalid files instead of throwing. */
    fun decode(text: String): Result<DesignProjectFile> =
        runCatching { json.decodeFromString(DesignProjectFile.serializer(), text) }
}
