package com.pixelcraft.studio.design

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.roundToInt

/**
 * Draws a [DesignDocument] on an Android canvas. One instance must be used
 * from one thread at a time (it reuses paints and caches text layouts).
 */
class DesignRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val textLayouts = HashMap<TextKey, StaticLayout>()

    private data class TextKey(val content: LayerContent.Text, val width: Int)

    private val checkerPaint: Paint by lazy {
        val tile = 32
        val bitmap = Bitmap.createBitmap(tile * 2, tile * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val gray = Paint().apply { color = 0xFFE2E8F0.toInt() }
        canvas.drawRect(0f, 0f, tile.toFloat(), tile.toFloat(), gray)
        canvas.drawRect(tile.toFloat(), tile.toFloat(), tile * 2f, tile * 2f, gray)
        Paint().apply { shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    }

    /**
     * Draws the background and every visible layer. The canvas must already
     * be in document pixel space. Set [showTransparencyGrid] only for the
     * on-screen view, never for export.
     */
    fun draw(
        canvas: Canvas,
        document: DesignDocument,
        images: Map<String, Bitmap>,
        showTransparencyGrid: Boolean
    ) {
        if (document.backgroundArgb != 0) {
            canvas.drawColor(document.backgroundArgb)
        } else if (showTransparencyGrid) {
            canvas.drawRect(0f, 0f, document.width.toFloat(), document.height.toFloat(), checkerPaint)
        }

        for (layer in document.layers) {
            if (layer.visible) drawLayer(canvas, layer, images)
        }
    }

    fun measureTextHeight(content: LayerContent.Text, width: Int): Float =
        buildLayout(content, width.coerceAtLeast(1)).height.toFloat()

    private fun drawLayer(canvas: Canvas, layer: Layer, images: Map<String, Bitmap>) {
        val t = layer.transform
        val saved = canvas.save()
        canvas.translate(t.centerX, t.centerY)
        canvas.rotate(t.rotationDeg)
        rect.set(-t.width / 2f, -t.height / 2f, t.width / 2f, t.height / 2f)

        when (val content = layer.content) {
            is LayerContent.Image -> drawImage(canvas, content, layer.opacity, images)
            is LayerContent.Shape -> drawShape(canvas, content, layer.opacity)
            is LayerContent.Text -> drawText(canvas, content, t.width, layer.opacity)
        }
        canvas.restoreToCount(saved)
    }

    private fun drawImage(
        canvas: Canvas,
        content: LayerContent.Image,
        opacity: Float,
        images: Map<String, Bitmap>
    ) {
        val bitmap = images[content.sourceId] ?: return
        paint.style = Paint.Style.FILL
        paint.alpha = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        canvas.drawBitmap(bitmap, null, rect, paint)
    }

    private fun drawShape(canvas: Canvas, content: LayerContent.Shape, opacity: Float) {
        paint.style = Paint.Style.FILL
        paint.color = withOpacity(content.fillArgb, opacity)
        when (content.kind) {
            ShapeKind.RECTANGLE -> canvas.drawRect(rect, paint)
            ShapeKind.ELLIPSE -> canvas.drawOval(rect, paint)
            ShapeKind.LINE -> {
                val radius = rect.height() / 2f
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }

        if (content.kind != ShapeKind.LINE &&
            content.strokeWidth > 0f &&
            Color.alpha(content.strokeArgb) > 0
        ) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = content.strokeWidth
            paint.color = withOpacity(content.strokeArgb, opacity)
            if (content.kind == ShapeKind.RECTANGLE) canvas.drawRect(rect, paint)
            else canvas.drawOval(rect, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawText(
        canvas: Canvas,
        content: LayerContent.Text,
        widthPx: Float,
        opacity: Float
    ) {
        val layout = layoutFor(content, widthPx.roundToInt().coerceAtLeast(1))
        layout.paint.color = withOpacity(content.colorArgb, opacity)
        val saved = canvas.save()
        canvas.translate(-widthPx / 2f, -layout.height / 2f)
        layout.draw(canvas)
        canvas.restoreToCount(saved)
    }

    private fun layoutFor(content: LayerContent.Text, width: Int): StaticLayout {
        val key = TextKey(content, width)
        textLayouts[key]?.let { return it }
        if (textLayouts.size > MAX_CACHED_LAYOUTS) textLayouts.clear()
        val layout = buildLayout(content, width)
        textLayouts[key] = layout
        return layout
    }

    private fun buildLayout(content: LayerContent.Text, width: Int): StaticLayout {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = content.sizePx
            color = content.colorArgb
            typeface = if (content.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        return StaticLayout.Builder
            .obtain(content.text, 0, content.text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
    }

    private fun withOpacity(argb: Int, opacity: Float): Int {
        val alpha = (Color.alpha(argb) * opacity.coerceIn(0f, 1f)).roundToInt()
        return (alpha shl 24) or (argb and 0x00FFFFFF)
    }

    private companion object {
        const val MAX_CACHED_LAYOUTS = 64
    }
}
