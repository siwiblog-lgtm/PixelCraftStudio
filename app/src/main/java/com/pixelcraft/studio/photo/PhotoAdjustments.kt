package com.pixelcraft.studio.photo

import android.graphics.ColorMatrix

/**
 * Colour adjustments for a photo. Every value is in the range -1..1,
 * where 0 means "unchanged".
 */
data class PhotoAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f
) {
    val isDefault: Boolean
        get() = this == PhotoAdjustments()

    /** Builds a single ColorMatrix combining saturation, contrast and brightness. */
    fun toColorMatrix(): ColorMatrix {
        val result = ColorMatrix()

        // Saturation: 0 = grayscale, 1 = original, 2 = double saturation.
        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(1f + saturation.coerceIn(MIN, MAX))
        result.postConcat(saturationMatrix)

        // Contrast scales colours around mid-gray; brightness shifts them.
        val scale = 1f + contrast.coerceIn(MIN, MAX)
        val translate = (-0.5f * scale + 0.5f) * 255f + brightness.coerceIn(MIN, MAX) * 255f
        val contrastBrightness = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        result.postConcat(contrastBrightness)

        return result
    }

    companion object {
        const val MIN = -1f
        const val MAX = 1f
    }
}
