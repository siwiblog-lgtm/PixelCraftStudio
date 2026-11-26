package com.pixelcraft.studio.ui.screens

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pixelcraft.studio.R
import com.pixelcraft.studio.core.ads.AdManager
import com.pixelcraft.studio.core.datastore.AppSettings
import com.pixelcraft.studio.core.datastore.SettingsDataStore
import com.pixelcraft.studio.data.LocalStorageManager
import com.pixelcraft.studio.domain.PixelEngine
import com.pixelcraft.studio.model.PixelCanvasDocument
import com.pixelcraft.studio.model.ToolMode
import kotlinx.coroutines.launch
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    initialDocument: PixelCanvasDocument,
    settings: AppSettings,
    settingsDataStore: SettingsDataStore,
    storageManager: LocalStorageManager,
    adManager: AdManager,
    isRtl: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var document by remember(initialDocument.id) { mutableStateOf(initialDocument) }
    var selectedTool by remember { mutableStateOf(ToolMode.PEN) }
    var selectedColor by remember { mutableStateOf(PixelEngine.defaultPalette[2]) }
    var showGrid by remember { mutableStateOf(true) }
    var shapeFilled by remember { mutableStateOf(false) }

    val undoStack = remember(initialDocument.id) { mutableStateListOf<IntArray>() }
    val redoStack = remember(initialDocument.id) { mutableStateListOf<IntArray>() }

    var dragStartCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragCurrentCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var actionCounter by remember { mutableIntStateOf(0) }

    fun pushUndoSnapshot(beforePixels: IntArray) {
        undoStack.add(beforePixels.copyOf())
        if (undoStack.size > 40) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun applyPixelsChange(newPixels: IntArray, recordUndo: Boolean = true) {
        if (newPixels.contentEquals(document.pixelsArgb)) return
        if (recordUndo) {
            pushUndoSnapshot(document.pixelsArgb)
        }
        document = document.copyWithPixels(newPixels)
        actionCounter += 1
    }

    fun performUndo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(document.pixelsArgb.copyOf())
        document = document.copyWithPixels(previous)
    }

    fun performRedo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(document.pixelsArgb.copyOf())
        document = document.copyWithPixels(next)
    }

    fun applyToolAtCell(x: Int, y: Int, isDragContinuation: Boolean) {
        if (x !in 0 until document.width || y !in 0 until document.height) return
        when (selectedTool) {
            ToolMode.PEN -> {
                val updated = PixelEngine.paintPixel(
                    pixels = document.pixelsArgb,
                    width = document.width,
                    height = document.height,
                    x = x,
                    y = y,
                    colorArgb = selectedColor.toArgb()
                )
                applyPixelsChange(updated, recordUndo = !isDragContinuation)
            }

            ToolMode.ERASER -> {
                val updated = PixelEngine.paintPixel(
                    pixels = document.pixelsArgb,
                    width = document.width,
                    height = document.height,
                    x = x,
                    y = y,
                    colorArgb = Color.Transparent.toArgb()
                )
                applyPixelsChange(updated, recordUndo = !isDragContinuation)
            }

            ToolMode.FILL -> {
                if (!isDragContinuation) {
                    val updated = PixelEngine.floodFill(
                        pixels = document.pixelsArgb,
                        width = document.width,
                        height = document.height,
                        startX = x,
                        startY = y,
                        replacementArgb = selectedColor.toArgb()
                    )
                    applyPixelsChange(updated, recordUndo = true)
                }
            }

            ToolMode.EYEDROPPER -> {
                val pickedArgb = document.pixelsArgb[y * document.width + x]
                if (android.graphics.Color.alpha(pickedArgb) > 0) {
                    selectedColor = Color(pickedArgb)
                    selectedTool = ToolMode.PEN
                }
            }

            ToolMode.LINE, ToolMode.RECTANGLE, ToolMode.CIRCLE -> {
                if (dragStartCell == null) {
                    dragStartCell = x to y
                    pushUndoSnapshot(document.pixelsArgb)
                }
                dragCurrentCell = x to y
            }
        }
    }

    fun commitShapeIfNeeded() {
        val start = dragStartCell
        val end = dragCurrentCell
        dragStartCell = null
        dragCurrentCell = null
        if (start == null || end == null) return

        val baseForShape = if (undoStack.isNotEmpty()) undoStack.last() else document.pixelsArgb
        val updated = when (selectedTool) {
            ToolMode.LINE -> PixelEngine.drawLine(
                pixels = baseForShape,
                width = document.width,
                height = document.height,
                x0 = start.first,
                y0 = start.second,
                x1 = end.first,
                y1 = end.second,
                colorArgb = selectedColor.toArgb()
            )

            ToolMode.RECTANGLE -> PixelEngine.drawRectangle(
                pixels = baseForShape,
                width = document.width,
                height = document.height,
                x0 = start.first,
                y0 = start.second,
                x1 = end.first,
                y1 = end.second,
                colorArgb = selectedColor.toArgb(),
                filled = shapeFilled
            )

            ToolMode.CIRCLE -> PixelEngine.drawEllipse(
                pixels = baseForShape,
                width = document.width,
                height = document.height,
                x0 = start.first,
                y0 = start.second,
                x1 = end.first,
                y1 = end.second,
                colorArgb = selectedColor.toArgb(),
                filled = shapeFilled
            )

            else -> baseForShape
        }
        document = document.copyWithPixels(updated)
        actionCounter += 1
    }

    fun triggerInterstitialIfThresholdReached(onDone: () -> Unit) {
        scope.launch {
            val count = settingsDataStore.incrementInterstitialCounter()
            if (count >= 4 && activity != null) {
                settingsDataStore.resetInterstitialCounter()
                adManager.showInterstitialIfReady(activity) {
                    onDone()
                }
            } else {
                onDone()
            }
        }
    }

    fun saveProjectLocally() {
        scope.launch {
            storageManager.saveProject(document).onSuccess {
                snackbarHostState.showSnackbar(context.getString(R.string.saved_locally))
            }.onFailure { err ->
                snackbarHostState.showSnackbar(err.localizedMessage ?: context.getString(R.string.error_generic))
            }
        }
    }

    fun exportToGallery() {
        scope.launch {
            storageManager.saveProject(document)
            storageManager.exportDocumentToGallery(
                doc = document,
                format = settings.exportFormat,
                quality = settings.exportQuality,
                upscaleFactor = 16
            ).onSuccess { fileName ->
                snackbarHostState.showSnackbar(context.getString(R.string.exported_to_gallery, fileName))
                triggerInterstitialIfThresholdReached {}
            }.onFailure { err ->
                snackbarHostState.showSnackbar(err.localizedMessage ?: context.getString(R.string.error_generic))
            }
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = document.title,
                            onValueChange = { newTitle ->
                                document = document.copy(title = newTitle.take(32))
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.width(170.dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { performUndo() },
                            enabled = undoStack.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.undo)
                            )
                        }
                        IconButton(
                            onClick = { performRedo() },
                            enabled = redoStack.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = stringResource(R.string.redo)
                            )
                        }
                        IconButton(onClick = { saveProjectLocally() }) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = stringResource(R.string.save_project)
                            )
                        }
                        IconButton(onClick = { exportToGallery() }) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = stringResource(R.string.export_image)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Canvas Info & Quick Transform Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${document.width} × ${document.height}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { showGrid = !showGrid }) {
                            Icon(
                                imageVector = Icons.Filled.GridOn,
                                contentDescription = stringResource(R.string.toggle_grid),
                                tint = if (showGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            pushUndoSnapshot(document.pixelsArgb)
                            document = PixelEngine.rotate90Clockwise(document)
                        }) {
                            Icon(
                                imageVector = Icons.Filled.RotateRight,
                                contentDescription = stringResource(R.string.rotate_90)
                            )
                        }
                        IconButton(onClick = {
                            pushUndoSnapshot(document.pixelsArgb)
                            document = PixelEngine.flipHorizontal(document)
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Flip,
                                contentDescription = stringResource(R.string.flip_horizontal)
                            )
                        }
                        if (selectedTool == ToolMode.RECTANGLE || selectedTool == ToolMode.CIRCLE) {
                            FilterChip(
                                selected = shapeFilled,
                                onClick = { shapeFilled = !shapeFilled },
                                label = { Text(stringResource(R.string.fill_shape)) }
                            )
                        }
                    }
                }

                // Pixel Art Drawing Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val aspectRatio = document.width.toFloat() / document.height.toFloat().coerceAtLeast(1f)
                    val adjustedPixels = remember(document.pixelsArgb, document.adjustments) {
                        PixelEngine.applyColorAdjustmentsToPixels(document.pixelsArgb, document.adjustments)
                    }

                    Surface(
                        modifier = Modifier
                            .aspectRatio(aspectRatio)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(document.id, document.width, document.height, selectedTool, selectedColor) {
                                    detectTapGestures { offset ->
                                        val cellW = size.width / document.width.toFloat()
                                        val cellH = size.height / document.height.toFloat()
                                        val gx = floor(offset.x / cellW).toInt()
                                        val gy = floor(offset.y / cellH).toInt()
                                        applyToolAtCell(gx, gy, isDragContinuation = false)
                                    }
                                }
                                .pointerInput(document.id, document.width, document.height, selectedTool, selectedColor) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val cellW = size.width / document.width.toFloat()
                                            val cellH = size.height / document.height.toFloat()
                                            val gx = floor(offset.x / cellW).toInt()
                                            val gy = floor(offset.y / cellH).toInt()
                                            dragStartCell = gx to gy
                                            dragCurrentCell = gx to gy
                                            applyToolAtCell(gx, gy, isDragContinuation = false)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val cellW = size.width / document.width.toFloat()
                                            val cellH = size.height / document.height.toFloat()
                                            val gx = floor(change.position.x / cellW).toInt()
                                            val gy = floor(change.position.y / cellH).toInt()
                                            dragCurrentCell = gx to gy
                                            if (selectedTool == ToolMode.PEN || selectedTool == ToolMode.ERASER) {
                                                applyToolAtCell(gx, gy, isDragContinuation = true)
                                            }
                                        },
                                        onDragEnd = {
                                            commitShapeIfNeeded()
                                        },
                                        onDragCancel = {
                                            dragStartCell = null
                                            dragCurrentCell = null
                                        }
                                    )
                                }
                        ) {
                            val cellW = size.width / document.width.toFloat()
                            val cellH = size.height / document.height.toFloat()

                            // Checkerboard for transparent pixels
                            val lightCheck = Color(0xFFF3F4F6)
                            val darkCheck = Color(0xFFE5E7EB)
                            for (y in 0 until document.height) {
                                for (x in 0 until document.width) {
                                    val checkColor = if ((x + y) % 2 == 0) lightCheck else darkCheck
                                    drawRect(
                                        color = checkColor,
                                        topLeft = Offset(x * cellW, y * cellH),
                                        size = Size(cellW + 0.5f, cellH + 0.5f)
                                    )
                                }
                            }

                            // Draw pixels
                            for (y in 0 until document.height) {
                                for (x in 0 until document.width) {
                                    val argb = adjustedPixels[y * document.width + x]
                                    if (android.graphics.Color.alpha(argb) > 0) {
                                        drawRect(
                                            color = Color(argb),
                                            topLeft = Offset(x * cellW, y * cellH),
                                            size = Size(cellW + 0.5f, cellH + 0.5f)
                                        )
                                    }
                                }
                            }

                            // Preview active shape while dragging
                            val sCell = dragStartCell
                            val eCell = dragCurrentCell
                            if (sCell != null && eCell != null && selectedTool in listOf(
                                    ToolMode.LINE,
                                    ToolMode.RECTANGLE,
                                    ToolMode.CIRCLE
                                )
                            ) {
                                val left = minOf(sCell.first, eCell.first) * cellW
                                val top = minOf(sCell.second, eCell.second) * cellH
                                val w = (kotlin.math.abs(eCell.first - sCell.first) + 1) * cellW
                                val h = (kotlin.math.abs(eCell.second - sCell.second) + 1) * cellH
                                drawRect(
                                    color = selectedColor.copy(alpha = 0.35f),
                                    topLeft = Offset(left, top),
                                    size = Size(w, h)
                                )
                            }

                            // Optional pixel grid lines
                            if (showGrid) {
                                val gridColor = Color.Black.copy(alpha = 0.14f)
                                for (x in 0..document.width) {
                                    val px = x * cellW
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(px, 0f),
                                        end = Offset(px, size.height),
                                        strokeWidth = 1f
                                    )
                                }
                                for (y in 0..document.height) {
                                    val py = y * cellH
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(0f, py),
                                        end = Offset(size.width, py),
                                        strokeWidth = 1f
                                    )
                                }
                            }
                        }
                    }
                }

                // Color Palette Bar
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(PixelEngine.defaultPalette) { paletteColor ->
                        val isSelected = paletteColor.toArgb() == selectedColor.toArgb()
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 36.dp else 32.dp)
                                .clip(CircleShape)
                                .background(paletteColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = paletteColor },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = if (paletteColor == Color.White || paletteColor == Color(0xFFFFCA28)) {
                                        Color.Black
                                    } else {
                                        Color.White
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Tool Selector Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolChip(
                        label = stringResource(R.string.tool_pen),
                        icon = Icons.Filled.BorderColor,
                        selected = selectedTool == ToolMode.PEN,
                        onClick = { selectedTool = ToolMode.PEN }
                    )
                    ToolChip(
                        label = stringResource(R.string.tool_eraser),
                        icon = Icons.Outlined.AutoFixNormal,
                        selected = selectedTool == ToolMode.ERASER,
                        onClick = { selectedTool = ToolMode.ERASER }
                    )
                    ToolChip(
                        label = stringResource(R.string.tool_fill),
                        icon = Icons.Filled.FormatColorFill,
                        selected = selectedTool == ToolMode.FILL,
                        onClick = { selectedTool = ToolMode.FILL }
                    )
                    ToolChip(
                        label = stringResource(R.string.tool_eyedropper),
                        icon = Icons.Filled.Colorize,
                        selected = selectedTool == ToolMode.EYEDROPPER,
                        onClick = { selectedTool = ToolMode.EYEDROPPER }
                    )
                    ToolChip(
                        label = stringResource(R.string.tool_line),
                        icon = Icons.Filled.ShowChart,
                        selected = selectedTool == ToolMode.LINE,
                        onClick = { selectedTool = ToolMode.LINE }
                    )
                    ToolChip(
                        label = stringResource(R.string.tool_rect),
                        icon = Icons.Filled.CropSquare,
                        selected = selectedTool == ToolMode.RECTANGLE,
                        onClick = { selectedTool = ToolMode.RECTANGLE }
                    )
                    ToolChip(
                        label = stringResource(R.string.tool_circle),
                        icon = Icons.Filled.Circle,
                        selected = selectedTool == ToolMode.CIRCLE,
                        onClick = { selectedTool = ToolMode.CIRCLE }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { exportToGallery() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.export_image))
                }
            }
        }
    }
}

@Composable
private fun ToolChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}
