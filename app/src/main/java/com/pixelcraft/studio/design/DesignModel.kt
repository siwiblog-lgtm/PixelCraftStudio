package com.pixelcraft.studio.design

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class ShapeKind { RECTANGLE, ELLIPSE, LINE }

/** What a layer draws. Pure data, so documents are cheap to copy and save. */
sealed interface LayerContent {

    /** A photo stored outside the document and referenced by [sourceId]. */
    data class Image(val sourceId: String) : LayerContent

    data class Text(
        val text: String,
        val sizePx: Float = 64f,
        val colorArgb: Int = 0xFFFFFFFF.toInt(),
        val bold: Boolean = false
    ) : LayerContent

    data class Shape(
        val kind: ShapeKind,
        val fillArgb: Int = 0xFF6366F1.toInt(),
        val strokeArgb: Int = 0,
        val strokeWidth: Float = 0f
    ) : LayerContent
}

/**
 * Position and size of a layer in canvas pixels. The anchor is the layer
 * centre, so scaling and rotating keep the layer in place.
 */
data class LayerTransform(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotationDeg: Float = 0f
) {
    fun movedBy(dx: Float, dy: Float): LayerTransform =
        copy(centerX = centerX + dx, centerY = centerY + dy)

    /** Uniform scale that never shrinks the layer below [MIN_SIZE]. */
    fun scaledBy(factor: Float): LayerTransform {
        if (factor <= 0f) return this
        val smallest = minOf(width, height).coerceAtLeast(1f)
        val limited = maxOf(factor, MIN_SIZE / smallest)
        return copy(width = width * limited, height = height * limited)
    }

    fun rotatedBy(deg: Float): LayerTransform =
        copy(rotationDeg = (((rotationDeg + deg) % 360f) + 360f) % 360f)

    /** True when the canvas point lies inside the (possibly rotated) layer. */
    fun containsPoint(px: Float, py: Float): Boolean {
        val dx = px - centerX
        val dy = py - centerY
        val rad = rotationDeg * PI / 180.0
        val localX = dx * cos(rad) + dy * sin(rad)
        val localY = -dx * sin(rad) + dy * cos(rad)
        return abs(localX) <= width / 2f && abs(localY) <= height / 2f
    }

    companion object {
        const val MIN_SIZE = 8f
    }
}

data class Layer(
    val id: String,
    val name: String,
    val content: LayerContent,
    val transform: LayerTransform,
    val opacity: Float = 1f,
    val visible: Boolean = true,
    val locked: Boolean = false
)

/** Layers are ordered from bottom (index 0) to top. Background 0 = transparent. */
data class DesignDocument(
    val width: Int,
    val height: Int,
    val backgroundArgb: Int = 0,
    val layers: List<Layer> = emptyList()
) {
    init {
        require(width in 1..MAX_SIDE && height in 1..MAX_SIDE) {
            "Canvas size must be between 1 and $MAX_SIDE pixels"
        }
    }

    fun layerById(id: String): Layer? = layers.firstOrNull { it.id == id }

    fun indexOf(id: String): Int = layers.indexOfFirst { it.id == id }

    /** Top-most visible layer under the given canvas point. */
    fun hitTest(x: Float, y: Float): Layer? =
        layers.lastOrNull { it.visible && it.transform.containsPoint(x, y) }

    companion object {
        const val MAX_SIDE = 8192
    }
}
