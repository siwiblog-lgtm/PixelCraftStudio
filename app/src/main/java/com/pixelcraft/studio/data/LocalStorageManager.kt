package com.pixelcraft.studio.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.pixelcraft.studio.model.CanvasColorAdjustments
import com.pixelcraft.studio.model.PixelCanvasDocument
import com.pixelcraft.studio.model.SavedProjectSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID

class LocalStorageManager(private val context: Context) {

    private val projectsDir: File by lazy {
        File(context.filesDir, "pixelcraft_projects").apply { mkdirs() }
    }

    suspend fun listSavedProjects(): List<SavedProjectSummary> = withContext(Dispatchers.IO) {
        val files = projectsDir.listFiles { file -> file.extension == "json" } ?: return@withContext emptyList()
        files.mapNotNull { file ->
            runCatching {
                val obj = JSONObject(file.readText(Charsets.UTF_8))
                SavedProjectSummary(
                    id = obj.optString("id", file.nameWithoutExtension),
                    title = obj.optString("title", file.nameWithoutExtension),
                    width = obj.optInt("width", 16),
                    height = obj.optInt("height", 16),
                    filePath = file.absolutePath,
                    updatedAtEpochMs = obj.optLong("updatedAtEpochMs", file.lastModified())
                )
            }.getOrNull()
        }.sortedByDescending { it.updatedAtEpochMs }
    }

    suspend fun saveProject(doc: PixelCanvasDocument): Result<SavedProjectSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(projectsDir, "${doc.id}.json")
            val pixelsArray = JSONArray()
            for (argb in doc.pixelsArgb) {
                pixelsArray.put(argb)
            }
            val adjObj = JSONObject().apply {
                put("brightness", doc.adjustments.brightness.toDouble())
                put("contrast", doc.adjustments.contrast.toDouble())
                put("saturation", doc.adjustments.saturation.toDouble())
                put("rotationDeg", doc.adjustments.rotationDeg)
                put("flipHorizontal", doc.adjustments.flipHorizontal)
                put("flipVertical", doc.adjustments.flipVertical)
            }
            val root = JSONObject().apply {
                put("id", doc.id)
                put("title", doc.title)
                put("width", doc.width)
                put("height", doc.height)
                put("updatedAtEpochMs", System.currentTimeMillis())
                put("adjustments", adjObj)
                put("pixels", pixelsArray)
            }
            file.writeText(root.toString(), Charsets.UTF_8)

            SavedProjectSummary(
                id = doc.id,
                title = doc.title,
                width = doc.width,
                height = doc.height,
                filePath = file.absolutePath,
                updatedAtEpochMs = file.lastModified()
            )
        }
    }

    suspend fun loadProject(id: String): Result<PixelCanvasDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(projectsDir, "$id.json")
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val width = root.getInt("width")
            val height = root.getInt("height")
            val pixelsArray = root.getJSONArray("pixels")
            val pixels = IntArray(width * height)
            for (i in 0 until minOf(pixels.size, pixelsArray.length())) {
                pixels[i] = pixelsArray.getInt(i)
            }
            val adjObj = root.optJSONObject("adjustments")
            val adjustments = CanvasColorAdjustments(
                brightness = adjObj?.optDouble("brightness", 0.0)?.toFloat() ?: 0f,
                contrast = adjObj?.optDouble("contrast", 1.0)?.toFloat() ?: 1f,
                saturation = adjObj?.optDouble("saturation", 1.0)?.toFloat() ?: 1f,
                rotationDeg = adjObj?.optInt("rotationDeg", 0) ?: 0,
                flipHorizontal = adjObj?.optBoolean("flipHorizontal", false) ?: false,
                flipVertical = adjObj?.optBoolean("flipVertical", false) ?: false
            )
            PixelCanvasDocument(
                id = root.optString("id", id),
                title = root.optString("title", "Pixel Art"),
                width = width,
                height = height,
                pixelsArgb = pixels,
                adjustments = adjustments,
                updatedAtEpochMs = root.optLong("updatedAtEpochMs", file.lastModified())
            )
        }
    }

    suspend fun deleteProject(id: String): Boolean = withContext(Dispatchers.IO) {
        File(projectsDir, "$id.json").delete()
    }

    suspend fun importImageAsPixelGrid(
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int
    ): Result<PixelCanvasDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Cannot open image stream")
            val decoded = stream.use { BitmapFactory.decodeStream(it) }
                ?: error("Failed to decode image")

            val w = targetWidth.coerceIn(8, 64)
            val h = targetHeight.coerceIn(8, 64)
            // Nearest-neighbor scaling preserves crisp pixel edges
            val scaled = Bitmap.createScaledBitmap(decoded, w, h, false)
            if (scaled != decoded) decoded.recycle()

            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
            scaled.recycle()

            PixelCanvasDocument(
                id = UUID.randomUUID().toString(),
                title = "Imported Pixel",
                width = w,
                height = h,
                pixelsArgb = pixels
            )
        }
    }

    suspend fun exportDocumentToGallery(
        doc: PixelCanvasDocument,
        format: String,
        quality: Int,
        upscaleFactor: Int = 16
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseBitmap = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
            baseBitmap.setPixels(doc.pixelsArgb, 0, doc.width, 0, 0, doc.width, doc.height)

            val processedBitmap = applyAdjustments(baseBitmap, doc.adjustments)
            if (processedBitmap != baseBitmap) baseBitmap.recycle()

            val scale = upscaleFactor.coerceIn(1, 32)
            val finalBitmap = Bitmap.createScaledBitmap(
                processedBitmap,
                processedBitmap.width * scale,
                processedBitmap.height * scale,
                false
            )
            if (finalBitmap != processedBitmap) processedBitmap.recycle()

            val isJpg = format.equals("JPG", ignoreCase = true) || format.equals("JPEG", ignoreCase = true)
            val mimeType = if (isJpg) "image/jpeg" else "image/png"
            val ext = if (isJpg) "jpg" else "png"
            val compressFormat = if (isJpg) {
                Bitmap.CompressFormat.JPEG
            } else {
                Bitmap.CompressFormat.PNG
            }

            val sanitizedTitle = doc.title.replace(Regex("[^a-zA-Z0-9_\\-\\u0600-\\u06FF]"), "_")
            val fileName = "${sanitizedTitle}_${System.currentTimeMillis()}.$ext"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/PixelCraftStudio"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val itemUri = context.contentResolver.insert(collection, values)
                ?: error("Failed to create gallery entry")

            val out: OutputStream = context.contentResolver.openOutputStream(itemUri)
                ?: error("Failed to open output stream")

            out.use { stream ->
                val ok = finalBitmap.compress(compressFormat, quality.coerceIn(50, 100), stream)
                if (!ok) error("Bitmap compression failed")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(itemUri, doneValues, null, null)
            }

            finalBitmap.recycle()
            fileName
        }
    }

    private fun applyAdjustments(source: Bitmap, adj: CanvasColorAdjustments): Bitmap {
        val matrix = Matrix().apply {
            val cx = source.width / 2f
            val cy = source.height / 2f
            if (adj.rotationDeg != 0) {
                postRotate(adj.rotationDeg.toFloat(), cx, cy)
            }
            val sx = if (adj.flipHorizontal) -1f else 1f
            val sy = if (adj.flipVertical) -1f else 1f
            if (sx != 1f || sy != 1f) {
                postScale(sx, sy, cx, cy)
            }
        }

        val transformed = Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, matrix, false
        )

        val satMatrix = ColorMatrix().apply { setSaturation(adj.saturation) }
        val c = adj.contrast
        val b = adj.brightness * 255f
        val translate = (-0.5f * c + 0.5f) * 255f + b
        val bcMatrix = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        bcMatrix.postConcat(satMatrix)

        val result = Bitmap.createBitmap(
            transformed.width, transformed.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)
        val paint = Paint().apply {
            isFilterBitmap = false
            colorFilter = ColorMatrixColorFilter(bcMatrix)
        }
        canvas.drawBitmap(transformed, 0f, 0f, paint)
        if (transformed != source) transformed.recycle()
        return result
    }
}
