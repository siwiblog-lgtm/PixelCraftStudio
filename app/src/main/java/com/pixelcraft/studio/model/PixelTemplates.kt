package com.pixelcraft.studio.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.UUID

object PixelTemplates {

    private val transparent = Color.Transparent.toArgb()
    private val ink = Color(0xFF14181F).toArgb()
    private val crimson = Color(0xFFE53935).toArgb()
    private val coral = Color(0xFFFF7043).toArgb()
    private val gold = Color(0xFFFFCA28).toArgb()
    private val teal = Color(0xFF00ACC1).toArgb()
    private val mint = Color(0xFF66BB6A).toArgb()

    val builtInTemplates: List<PixelTemplate> = listOf(
        PixelTemplate(
            id = "tpl_heart_16",
            nameKey = "heart",
            width = 16,
            height = 16,
            accentColor = Color(0xFFE53935),
            buildPixels = { buildHeart16x16() }
        ),
        PixelTemplate(
            id = "tpl_sun_16",
            nameKey = "sun",
            width = 16,
            height = 16,
            accentColor = Color(0xFFFFCA28),
            buildPixels = { buildSun16x16() }
        ),
        PixelTemplate(
            id = "tpl_gem_16",
            nameKey = "gem",
            width = 16,
            height = 16,
            accentColor = Color(0xFF00ACC1),
            buildPixels = { buildGem16x16() }
        )
    )

    fun createBlankDocument(width: Int, height: Int, title: String = "Pixel Art"): PixelCanvasDocument {
        val safeW = width.coerceIn(8, 64)
        val safeH = height.coerceIn(8, 64)
        return PixelCanvasDocument(
            id = UUID.randomUUID().toString(),
            title = title,
            width = safeW,
            height = safeH,
            pixelsArgb = IntArray(safeW * safeH) { transparent }
        )
    }

    private fun buildHeart16x16(): IntArray {
        val grid = IntArray(16 * 16) { transparent }
        val pattern = arrayOf(
            "..RRR......RRR..",
            ".RFFFR....RFFFR.",
            "RFFFFFR..RFFFFFR",
            "RFFFFFFRRFFFFFFR",
            "RFFFFFFFFFFFFFFR",
            "RFFFFFFFFFFFFFFR",
            ".RFFFFFFFFFFFFR.",
            "..RFFFFFFFFFFR..",
            "...RFFFFFFFFR...",
            "....RFFFFFFR....",
            ".....RFFFFR.....",
            "......RFFR......",
            ".......RR......."
        )
        pattern.forEachIndexed { rowIdx, line ->
            val y = rowIdx + 1
            for (x in 0 until minOf(16, line.length)) {
                grid[y * 16 + x] = when (line[x]) {
                    'R' -> crimson
                    'F' -> coral
                    else -> transparent
                }
            }
        }
        return grid
    }

    private fun buildSun16x16(): IntArray {
        val grid = IntArray(16 * 16) { transparent }
        for (y in 4..11) {
            for (x in 4..11) {
                val dx = x - 7.5
                val dy = y - 7.5
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist <= 3.7) {
                    grid[y * 16 + x] = gold
                }
            }
        }
        val rays = listOf(
            1 to 7, 2 to 7,
            13 to 7, 14 to 7,
            7 to 1, 7 to 2,
            7 to 13, 7 to 14,
            3 to 3, 12 to 3,
            3 to 12, 12 to 12
        )
        for ((rx, ry) in rays) {
            grid[ry * 16 + rx] = gold
        }
        return grid
    }

    private fun buildGem16x16(): IntArray {
        val grid = IntArray(16 * 16) { transparent }
        val pattern = arrayOf(
            "....TTTTTTTT....",
            "...TMMMMMMMMT...",
            "..TTMMMMMMMMTT..",
            ".TTTTTTTTTTTTTT.",
            "..TTTTTTTTTTTT..",
            "...TTTTTTTTTT...",
            "....TTTTTTTT....",
            ".....TTTTTT.....",
            "......TTTT......",
            ".......TT......."
        )
        pattern.forEachIndexed { rowIdx, line ->
            val y = rowIdx + 3
            for (x in 0 until minOf(16, line.length)) {
                grid[y * 16 + x] = when (line[x]) {
                    'T' -> teal
                    'M' -> mint
                    else -> transparent
                }
            }
        }
        // Subtle dark outline on top edge
        for (x in 4..11) {
            if (grid[3 * 16 + x] == transparent) grid[3 * 16 + x] = ink
        }
        return grid
    }
}
