package com.pixelcraft.studio.photo

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

enum class ExportFormat(
    val mimeType: String,
    val extension: String,
    val supportsQuality: Boolean
) {
    PNG("image/png", "png", false),
    JPG("image/jpeg", "jpg", true),
    WEBP("image/webp", "webp", true)
}

/** Loading photos from the device and saving results to the gallery. */
object PhotoIO {

    /** Longest side of the working bitmap (limits memory use). */
    const val EDIT_MAX_SIDE = 3072

    /** Longest side of the small bitmap used for on-screen previews. */
    const val PREVIEW_MAX_SIDE = 1280

    fun load(resolver: ContentResolver, uri: Uri, maxSide: Int = EDIT_MAX_SIDE): Bitmap {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1)
                )
            }
        }
    }

    fun createPreview(full: Bitmap, maxSide: Int = PREVIEW_MAX_SIDE): Bitmap {
        val longest = max(full.width, full.height)
        if (longest <= maxSide) return full
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            full,
            (full.width * scale).roundToInt().coerceAtLeast(1),
            (full.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    }

    /** Saves to Pictures/PixelCraft Studio and returns the new image Uri. */
    fun save(
        resolver: ContentResolver,
        bitmap: Bitmap,
        format: ExportFormat,
        quality: Int,
        displayName: String
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.${format.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PixelCraft Studio")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create the image entry")

        try {
            // JPEG has no transparency, so flatten onto white to avoid black areas.
            val toWrite = if (format == ExportFormat.JPG && bitmap.hasAlpha()) {
                flattenOnWhite(bitmap)
            } else {
                bitmap
            }

            val stream = resolver.openOutputStream(uri)
                ?: throw IOException("Could not open the output stream")
            stream.use { out ->
                val ok = toWrite.compress(compressFormat(format), quality.coerceIn(1, 100), out)
                if (!ok) throw IOException("Image compression failed")
            }

            if (toWrite !== bitmap) toWrite.recycle()

            val done = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return result
    }

    @Suppress("DEPRECATION")
    private fun compressFormat(format: ExportFormat): Bitmap.CompressFormat = when (format) {
        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
        ExportFormat.JPG -> Bitmap.CompressFormat.JPEG
        ExportFormat.WEBP ->
            if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
            else Bitmap.CompressFormat.WEBP
    }
}
