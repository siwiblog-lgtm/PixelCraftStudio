package com.pixelcraft.studio.model

import androidx.compose.ui.graphics.Color

enum class ToolMode {
    PEN,
    ERASER,
    FILL,
    EYEDROPPER,
    LINE,
    RECTANGLE,
    CIRCLE
}

data class CanvasColorAdjustments(
    val brightness: Float = 0f,   // -1f .. +1f
    val contrast: Float = 1f,     // 0.5f .. 2f
    val saturation: Float = 1f,   // 0f .. 2f
    val rotationDeg: Int = 0,     // 0, 90, 180, 270
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
)

data class PixelCanvasDocument(
    val id: String,
    val title: String,
    val width: Int,
    val height: Int,
    val pixelsArgb: IntArray, // size == width * height
    val adjustments: CanvasColorAdjustments = CanvasColorAdjustments(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun copyWithPixels(newPixels: IntArray): PixelCanvasDocument =
        copy(pixelsArgb = newPixels.copyOf(), updatedAtEpochMs = System.currentTimeMillis())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PixelCanvasDocument
        return id == other.id &&
            title == other.title &&
            width == other.width &&
            height == other.height &&
            pixelsArgb.contentEquals(other.pixelsArgb) &&
            adjustments == other.adjustments &&
            updatedAtEpochMs == other.updatedAtEpochMs
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + pixelsArgb.contentHashCode()
        result = 31 * result + adjustments.hashCode()
        result = 31 * result + updatedAtEpochMs.hashCode()
        return result
    }
}

data class PixelTemplate(
    val id: String,
    val nameKey: String,
    val width: Int,
    val height: Int,
    val accentColor: Color,
    val buildPixels: () -> IntArray
)

data class SavedProjectSummary(
    val id: String,
    val title: String,
    val width: Int,
    val height: Int,
    val filePath: String,
    val updatedAtEpochMs: Long
)
