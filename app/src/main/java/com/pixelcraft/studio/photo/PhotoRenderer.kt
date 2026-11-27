package com.pixelcraft.studio.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Replays a [PhotoEditState] on a source bitmap and returns a new bitmap. */
object PhotoRenderer {

    /**
     * @param maxSide when set, the result is scaled down so its longest side
     * is at most this many pixels (used for fast on-screen previews).
     * Pass null for a full-resolution export.
     */
    fun render(source: Bitmap, state: PhotoEditState, maxSide: Int? = null): Bitmap {
        val scale = if (maxSide != null && maxSide > 0) {
            min(1f, maxSide.toFloat() / max(source.width, source.height))
        } else {
            1f
        }

        val matrix = Matrix()
        matrix.postScale(
            if (state.flipHorizontal) -1f else 1f,
            if (state.flipVertical) -1f else 1f
        )
        matrix.postRotate(state.rotationQuarterTurns * 90f)
        if (scale < 1f) matrix.postScale(scale, scale)

        val transformed = Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, matrix, true
        )

        val crop = cropRect(transformed.width, transformed.height, state.cropAspect)
        val output = Bitmap.createBitmap(
            crop.width(), crop.height(), Bitmap.Config.ARGB_8888
        )

        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (!state.adjustments.isDefault) {
            paint.colorFilter = ColorMatrixColorFilter(state.adjustments.toColorMatrix())
        }

        Canvas(output).drawBitmap(
            transformed,
            -crop.left.toFloat(),
            -crop.top.toFloat(),
            paint
        )

        if (transformed !== source) transformed.recycle()
        return output
    }

    /** Largest centered rectangle with the requested aspect ratio. */
    internal fun cropRect(width: Int, height: Int, aspect: CropAspect): Rect {
        val ratio = aspect.ratio ?: return Rect(0, 0, width, height)
        val currentRatio = width.toFloat() / height

        return if (currentRatio > ratio) {
            val newWidth = (height * ratio).roundToInt().coerceIn(1, width)
            val left = (width - newWidth) / 2
            Rect(left, 0, left + newWidth, height)
        } else {
            val newHeight = (width / ratio).roundToInt().coerceIn(1, height)
            val top = (height - newHeight) / 2
            Rect(0, top, width, top + newHeight)
        }
    }
}
