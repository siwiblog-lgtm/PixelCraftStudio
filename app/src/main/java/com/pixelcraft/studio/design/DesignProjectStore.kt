package com.pixelcraft.studio.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.File
import java.io.FileOutputStream

/** Saves and loads design projects inside the app's private storage. */
class DesignProjectStore(context: Context) {

    private val root = File(context.applicationContext.filesDir, "design_projects")

    data class Summary(
        val id: String,
        val name: String,
        val updatedAtEpochMs: Long,
        val width: Int,
        val height: Int
    )

    class Loaded(
        val name: String,
        val document: DesignDocument,
        val images: Map<String, Bitmap>
    )

    fun exists(id: String): Boolean = isSafeName(id) && projectFile(id).exists()

    fun list(): List<Summary> {
        val directories = root.listFiles { file -> file.isDirectory } ?: return emptyList()
        return directories.mapNotNull { dir ->
            val file = File(dir, FILE_NAME)
            if (!file.exists()) {
                null
            } else {
                DesignSerializer.decode(file.readText()).getOrNull()?.let { parsed ->
                    Summary(
                        id = dir.name,
                        name = parsed.name,
                        updatedAtEpochMs = parsed.updatedAtEpochMs,
                        width = parsed.document.width,
                        height = parsed.document.height
                    )
                }
            }
        }.sortedByDescending { it.updatedAtEpochMs }
    }

    fun save(
        id: String,
        name: String,
        document: DesignDocument,
        images: Map<String, Bitmap>
    ) {
        require(isSafeName(id)) { "Invalid project id" }
        val dir = projectDir(id)
        val imageDir = File(dir, IMAGES_DIR)
        imageDir.mkdirs()

        // Images are written once; later saves only write the small JSON file.
        for (layer in document.layers) {
            val content = layer.content as? LayerContent.Image ?: continue
            if (!isSafeName(content.sourceId)) continue
            val target = File(imageDir, "${content.sourceId}.png")
            if (target.exists()) continue
            val bitmap = images[content.sourceId] ?: continue
            writePng(bitmap, target)
        }

        val text = DesignSerializer.encode(
            DesignProjectFile(
                name = name,
                updatedAtEpochMs = System.currentTimeMillis(),
                document = document
            )
        )
        val temp = File(dir, "$FILE_NAME.tmp")
        temp.writeText(text)
        val destination = projectFile(id)
        if (!temp.renameTo(destination)) {
            temp.copyTo(destination, overwrite = true)
            temp.delete()
        }
    }

    /** Returns null when the project is missing or its file is corrupt. */
    fun load(id: String): Loaded? {
        if (!isSafeName(id)) return null
        val file = projectFile(id)
        if (!file.exists()) return null
        val parsed = DesignSerializer.decode(file.readText()).getOrNull() ?: return null

        val images = HashMap<String, Bitmap>()
        for (layer in parsed.document.layers) {
            val content = layer.content as? LayerContent.Image ?: continue
            val sourceId = content.sourceId
            if (images.containsKey(sourceId) || !isSafeName(sourceId)) continue
            val imageFile = File(File(projectDir(id), IMAGES_DIR), "$sourceId.png")
            if (!imageFile.exists()) continue
            try {
                images[sourceId] = ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(imageFile)
                ) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (_: Exception) {
                // A missing image simply leaves that layer empty.
            } catch (_: OutOfMemoryError) {
                // Same as above; the rest of the project still opens.
            }
        }
        return Loaded(parsed.name, parsed.document, images)
    }

    fun delete(id: String) {
        if (isSafeName(id)) projectDir(id).deleteRecursively()
    }

    private fun writePng(bitmap: Bitmap, target: File) {
        val temp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(temp).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun projectDir(id: String) = File(root, id)

    private fun projectFile(id: String) = File(projectDir(id), FILE_NAME)

    /** Blocks path tricks such as "../" in ids read from disk. */
    private fun isSafeName(name: String): Boolean =
        name.isNotEmpty() && name.length <= 80 && name.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private companion object {
        const val FILE_NAME = "project.json"
        const val IMAGES_DIR = "images"
    }
}
