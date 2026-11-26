package com.pixelcraft.studio.domain

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pixelcraft.studio.model.CanvasColorAdjustments
import com.pixelcraft.studio.model.PixelCanvasDocument
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PixelEngine {

    val defaultPalette: List<Color> = listOf(
        Color(0xFF14181F),
        Color(0xFFFFFFFF),
        Color(0xFFE53935),
        Color(0xFFFF7043),
        Color(0xFFFFCA28),
        Color(0xFF66BB6A),
        Color(0xFF00ACC1),
        Color(0xFF1E88E5),
        Color(0xFF8E24AA),
        Color(0xFF6D4C41)
    )

    fun paintPixel(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        colorArgb: Int
    ): IntArray {
        if (x !in 0 until width || y !in 0 until height) return pixels
        val copy = pixels.copyOf()
        copy[y * width + x] = colorArgb
        return copy
    }

    fun floodFill(
        pixels: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        replacementArgb: Int
    ): IntArray {
        if (startX !in 0 until width || startY !in 0 until height) return pixels
        val targetArgb = pixels[startY * width + startX]
        if (targetArgb == replacementArgb) return pixels

        val result = pixels.copyOf()
        val visited = BooleanArray(width * height)
        val queue = ArrayDeque<Int>()
        val startIdx = startY * width + startX
        queue.add(startIdx)
        visited[startIdx] = true

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            result[idx] = replacementArgb
            val cx = idx % width
            val cy = idx / width

            val neighbors = arrayOf(
                cx - 1 to cy,
                cx + 1 to cy,
                cx to cy - 1,
                cx to cy + 1
            )
            for ((nx, ny) in neighbors) {
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (!visited[nIdx] && result[nIdx] == targetArgb) {
                        visited[nIdx] = true
                        queue.add(nIdx)
                    }
                }
            }
        }
        return result
    }

    fun drawLine(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        colorArgb: Int
    ): IntArray {
        val result = pixels.copyOf()
        var cx = x0
        var cy = y0
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy

        while (true) {
            if (cx in 0 until width && cy in 0 until height) {
                result[cy * width + cx] = colorArgb
            }
            if (cx == x1 && cy == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                cx += sx
            }
            if (e2 < dx) {
                err += dx
                cy += sy
            }
        }
        return result
    }

    fun drawRectangle(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        colorArgb: Int,
        filled: Boolean = false
    ): IntArray {
        val result = pixels.copyOf()
        val left = max(0, min(x0, x1))
        val right = min(width - 1, max(x0, x1))
        val top = max(0, min(y0, y1))
        val bottom = min(height - 1, max(y0, y1))

        for (y in top..bottom) {
            for (x in left..right) {
                val isBorder = x == left || x == right || y == top || y == bottom
                if (filled || isBorder) {
                    result[y * width + x] = colorArgb
                }
            }
        }
        return result
    }

    fun drawEllipse(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        colorArgb: Int,
        filled: Boolean = false
    ): IntArray {
        val result = pixels.copyOf()
        val left = min(x0, x1)
        val right = max(x0, x1)
        val top = min(y0, y1)
        val bottom = max(y0, y1)

        val rx = max(0.5, (right - left) / 2.0)
        val ry = max(0.5, (bottom - top) / 2.0)
        val cx = (left + right) / 2.0
        val cy = (top + bottom) / 2.0

        for (y in max(0, top)..min(height - 1, bottom)) {
            for (x in max(0, left)..min(width - 1, right)) {
                val nx = (x - cx) / rx
                val ny = (y - cy) / ry
                val normDist = nx * nx + ny * ny
                if (filled) {
                    if (normDist <= 1.05) result[y * width + x] = colorArgb
                } else {
                    if (normDist in 0.72..1.12) result[y * width + x] = colorArgb
                }
            }
        }
        return result
    }

    fun rotate90Clockwise(doc: PixelCanvasDocument): PixelCanvasDocument {
        val newW = doc.height
        val newH = doc.width
        val out = IntArray(newW * newH)
        for (y in 0 until doc.height) {
            for (x in 0 until doc.width) {
                val nx = doc.height - 1 - y
                val ny = x
                out[ny * newW + nx] = doc.pixelsArgb[y * doc.width + x]
            }
        }
        val nextRotation = (doc.adjustments.rotationDeg + 90) % 360
        return doc.copy(
            width = newW,
            height = newH,
            pixelsArgb = out,
            adjustments = doc.adjustments.copy(rotationDeg = nextRotation),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun flipHorizontal(doc: PixelCanvasDocument): PixelCanvasDocument {
        val out = IntArray(doc.width * doc.height)
        for (y in 0 until doc.height) {
            for (x in 0 until doc.width) {
                val nx = doc.width - 1 - x
                out[y * doc.width + nx] = doc.pixelsArgb[y * doc.width + x]
            }
        }
        return doc.copy(
            pixelsArgb = out,
            adjustments = doc.adjustments.copy(flipHorizontal = !doc.adjustments.flipHorizontal),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun applyColorAdjustmentsToPixels(
        pixelsArgb: IntArray,
        adjustments: CanvasColorAdjustments
    ): IntArray {
        val brightnessOffset = adjustments.brightness * 255f
        val contrast = adjustments.contrast
        val saturation = adjustments.saturation
        val midpoint = 127.5f

        return IntArray(pixelsArgb.size) { idx ->
            val argb = pixelsArgb[idx]
            val a = AndroidColor.alpha(argb)
            if (a == 0) return@IntArray Color.Transparent.toArgb()

            var r = AndroidColor.red(argb).toFloat()
            var g = AndroidColor.green(argb).toFloat()
            var b = AndroidColor.blue(argb).toFloat()

            // Contrast + Brightness
            r = ((r - midpoint) * contrast + midpoint + brightnessOffset).coerceIn(0f, 255f)
            g = ((g - midpoint) * contrast + midpoint + brightnessOffset).coerceIn(0f, 255f)
            b = ((b - midpoint) * contrast + midpoint + brightnessOffset).coerceIn(0f, 255f)

            // Saturation
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            r = (lum + (r - lum) * saturation).coerceIn(0f, 255f)
            g = (lum + (g - lum) * saturation).coerceIn(0f, 255f)
            b = (lum + (b - lum) * saturation).coerceIn(0f, 255f)

            AndroidColor.argb(
                a,
                r.roundToInt(),
                g.roundToInt(),
                b.roundToInt()
            )
        }
    }
}
