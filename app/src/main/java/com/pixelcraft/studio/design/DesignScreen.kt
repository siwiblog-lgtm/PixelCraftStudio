package com.pixelcraft.studio.design

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixelcraft.studio.R
import com.pixelcraft.studio.core.theme.StudioCanvasBackdrop
import com.pixelcraft.studio.photo.ExportFormat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private enum class TextDialogMode { NONE, ADD, EDIT }

private val ColorPalette = listOf(
    0xFFFFFFFF.toInt(), 0xFF111827.toInt(), 0xFFEF4444.toInt(), 0xFFF97316.toInt(),
    0xFFFACC15.toInt(), 0xFF22C55E.toInt(), 0xFF06B6D4.toInt(), 0xFF6366F1.toInt(),
    0xFFEC4899.toInt()
)

/** Maps between document pixels and screen pixels for the current view. */
private class ViewGeometry(val scale: Float, val originX: Float, val originY: Float) {

    fun toDoc(point: Offset) = Offset((point.x - originX) / scale, (point.y - originY) / scale)

    companion object {
        const val FIT_MARGIN = 0.92f

        fun fitScale(width: Float, height: Float, docW: Int, docH: Int): Float =
            min(width / docW, height / docH) * FIT_MARGIN

        fun compute(
            width: Float,
            height: Float,
            docW: Int,
            docH: Int,
            zoom: Float,
            pan: Offset
        ): ViewGeometry {
            val scale = fitScale(width, height, docW, docH) * zoom
            return ViewGeometry(
                scale = scale,
                originX = (width - docW * scale) / 2f + pan.x,
                originY = (height - docH * scale) / 2f + pan.y
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignScreen(
    isRtl: Boolean,
    onNavigateBack: () -> Unit,
    viewModel: DesignViewModel = viewModel()
) {
    val context = LocalContext.current
    val renderer = remember { DesignRenderer() }
    val selectionPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            color = 0xFF22D3EE.toInt()
        }
    }

    var viewZoom by remember { mutableFloatStateOf(1f) }
    var viewPan by remember { mutableStateOf(Offset.Zero) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    var textDialog by remember { mutableStateOf(TextDialogMode.NONE) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addImage(context.contentResolver, uri) {
                Toast.makeText(
                    context,
                    context.getString(R.string.photo_load_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.design_studio_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.undo() }, enabled = viewModel.canUndo) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.undo)
                            )
                        }
                        IconButton(onClick = { viewModel.redo() }, enabled = viewModel.canRedo) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = stringResource(R.string.redo)
                            )
                        }
                        IconButton(
                            onClick = { showSaveDialog = true },
                            enabled = !viewModel.isSaving
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = stringResource(R.string.export)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (viewModel.isSaving || viewModel.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(StudioCanvasBackdrop)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val canvasW = size.width.toFloat()
                                val canvasH = size.height.toFloat()
                                val doc = viewModel.document

                                // Decide at touch-down what this gesture controls.
                                val startGeometry = ViewGeometry.compute(
                                    canvasW, canvasH, doc.width, doc.height, viewZoom, viewPan
                                )
                                val docPoint = startGeometry.toDoc(down.position)
                                val hit = doc.hitTest(docPoint.x, docPoint.y)
                                if (hit != null && viewModel.selectedId != hit.id) {
                                    viewModel.select(hit.id)
                                }
                                val layerGesture = hit != null && !hit.locked

                                var pastSlop = false
                                var zoomAcc = 1f
                                var rotationAcc = 0f
                                var panAcc = Offset.Zero
                                val slop = viewConfiguration.touchSlop

                                do {
                                    val event = awaitPointerEvent()
                                    val canceled = event.changes.any { it.isConsumed }
                                    if (!canceled) {
                                        val zoomChange = event.calculateZoom()
                                        val rotationChange = event.calculateRotation()
                                        val panChange = event.calculatePan()

                                        if (!pastSlop) {
                                            zoomAcc *= zoomChange
                                            rotationAcc += rotationChange
                                            panAcc += panChange
                                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                            val zoomMotion = abs(1f - zoomAcc) * centroidSize
                                            val rotationMotion =
                                                abs(rotationAcc * PI.toFloat() * centroidSize / 180f)
                                            val panMotion = panAcc.getDistance()
                                            if (zoomMotion > slop || rotationMotion > slop || panMotion > slop) {
                                                pastSlop = true
                                            }
                                        }

                                        if (pastSlop) {
                                            val moved = zoomChange != 1f ||
                                                rotationChange != 0f ||
                                                panChange != Offset.Zero
                                            if (moved) {
                                                val geometry = ViewGeometry.compute(
                                                    canvasW, canvasH, doc.width, doc.height, viewZoom, viewPan
                                                )
                                                if (layerGesture) {
                                                    viewModel.transformSelected(
                                                        panChange.x / geometry.scale,
                                                        panChange.y / geometry.scale,
                                                        zoomChange,
                                                        rotationChange
                                                    )
                                                } else {
                                                    val centroid = event.calculateCentroid(useCurrent = false)
                                                    val docUnder = geometry.toDoc(centroid)
                                                    val newZoom = (viewZoom * zoomChange).coerceIn(1f, 8f)
                                                    val newScale = ViewGeometry.fitScale(
                                                        canvasW, canvasH, doc.width, doc.height
                                                    ) * newZoom
                                                    val wantedX = centroid.x + panChange.x - docUnder.x * newScale
                                                    val wantedY = centroid.y + panChange.y - docUnder.y * newScale
                                                    val baseX = (canvasW - doc.width * newScale) / 2f
                                                    val baseY = (canvasH - doc.height * newScale) / 2f
                                                    viewZoom = newZoom
                                                    viewPan = Offset(wantedX - baseX, wantedY - baseY)
                                                }
                                            }
                                            event.changes.forEach {
                                                if (it.positionChanged()) it.consume()
                                            }
                                        }
                                    }
                                } while (!canceled && event.changes.any { it.pressed })

                                if (layerGesture) viewModel.finishEdit()

                                if (!pastSlop) {
                                    val now = System.currentTimeMillis()
                                    if (hit == null) {
                                        viewModel.select(null)
                                        if (now - lastTapAt < 300L) {
                                            viewZoom = 1f
                                            viewPan = Offset.Zero
                                        }
                                    }
                                    lastTapAt = now
                                }
                            }
                        }
                ) {
                    val doc = viewModel.document
                    val geometry = ViewGeometry.compute(
                        size.width, size.height, doc.width, doc.height, viewZoom, viewPan
                    )
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas

                        val saved = native.save()
                        native.translate(geometry.originX, geometry.originY)
                        native.scale(geometry.scale, geometry.scale)
                        native.clipRect(0f, 0f, doc.width.toFloat(), doc.height.toFloat())
                        renderer.draw(native, doc, viewModel.images, showTransparencyGrid = true)
                        native.restoreToCount(saved)

                        val selected = viewModel.selectedLayer
                        if (selected != null) {
                            val t = selected.transform
                            val overlay = native.save()
                            native.translate(geometry.originX, geometry.originY)
                            native.scale(geometry.scale, geometry.scale)
                            native.translate(t.centerX, t.centerY)
                            native.rotate(t.rotationDeg)
                            selectionPaint.strokeWidth = 2.5f / geometry.scale
                            native.drawRect(
                                -t.width / 2f, -t.height / 2f,
                                t.width / 2f, t.height / 2f,
                                selectionPaint
                            )
                            native.restoreToCount(overlay)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 3.dp
                ) {
                    BottomPanel(
                        viewModel = viewModel,
                        onAddImage = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onAddText = { textDialog = TextDialogMode.ADD },
                        onEditText = { textDialog = TextDialogMode.EDIT }
                    )
                }
            }
        }

        if (textDialog != TextDialogMode.NONE) {
            val editing = textDialog == TextDialogMode.EDIT
            val initial = if (editing) {
                (viewModel.selectedLayer?.content as? LayerContent.Text)?.text.orEmpty()
            } else {
                stringResource(R.string.design_default_text)
            }
            TextDialog(
                initial = initial,
                onDismiss = { textDialog = TextDialogMode.NONE },
                onConfirm = { text ->
                    if (text.isNotBlank()) {
                        if (editing) viewModel.updateText(text) else viewModel.addText(text)
                    }
                    textDialog = TextDialogMode.NONE
                }
            )
        }

        if (showSaveDialog) {
            ExportDialog(
                onDismiss = { showSaveDialog = false },
                onConfirm = { format, quality ->
                    showSaveDialog = false
                    viewModel.export(context.contentResolver, format, quality) { result ->
                        val message = result.fold(
                            onSuccess = { context.getString(R.string.export_success) },
                            onFailure = {
                                context.getString(R.string.export_error, it.message ?: "")
                            }
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun BottomPanel(
    viewModel: DesignViewModel,
    onAddImage: () -> Unit,
    onAddText: () -> Unit,
    onEditText: () -> Unit
) {
    val layer = viewModel.selectedLayer
    val background = viewModel.document.backgroundArgb

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Add tools
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToolButton(Icons.Filled.AddPhotoAlternate, stringResource(R.string.tool_add_image), onAddImage)
            ToolButton(Icons.Filled.TextFields, stringResource(R.string.tool_add_text), onAddText)
            ToolButton(Icons.Filled.CropSquare, stringResource(R.string.shape_rect)) {
                viewModel.addShape(ShapeKind.RECTANGLE)
            }
            ToolButton(Icons.Filled.RadioButtonUnchecked, stringResource(R.string.shape_circle)) {
                viewModel.addShape(ShapeKind.ELLIPSE)
            }
            ToolButton(Icons.Filled.Remove, stringResource(R.string.shape_line)) {
                viewModel.addShape(ShapeKind.LINE)
            }
        }

        if (layer != null) {
            // Actions for the selected layer
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ActionIcon(Icons.Filled.ContentCopy, stringResource(R.string.duplicate)) {
                    viewModel.duplicateSelected()
                }
                ActionIcon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up)) {
                    viewModel.bringForward()
                }
                ActionIcon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down)) {
                    viewModel.sendBackward()
                }
                ActionIcon(
                    if (layer.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    stringResource(R.string.lock)
                ) { viewModel.toggleLock() }
                ActionIcon(
                    if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    stringResource(R.string.visibility)
                ) { viewModel.toggleVisible() }
                if (layer.content is LayerContent.Text) {
                    ActionIcon(Icons.Filled.Edit, stringResource(R.string.design_edit_text), onEditText)
                }
                ActionIcon(Icons.Filled.Delete, stringResource(R.string.delete)) {
                    viewModel.deleteSelected()
                }
            }

            // Opacity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.opacity), style = MaterialTheme.typography.bodyMedium)
                Text("${(layer.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = layer.opacity,
                onValueChange = { viewModel.setOpacityLive(it) },
                onValueChangeFinished = { viewModel.finishEdit() },
                valueRange = 0f..1f
            )

            // Colour
            if (layer.content !is LayerContent.Image) {
                Text(stringResource(R.string.fill_color), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ColorPalette.forEach { argb ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { viewModel.setColor(argb) }
                        )
                    }
                }
            }
        } else {
            // Canvas background
            Text(stringResource(R.string.canvas_bg), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = background == 0,
                    onClick = { viewModel.setBackground(0) },
                    label = { Text(stringResource(R.string.transparent)) }
                )
                FilterChip(
                    selected = background == 0xFFFFFFFF.toInt(),
                    onClick = { viewModel.setBackground(0xFFFFFFFF.toInt()) },
                    label = { Text(stringResource(R.string.design_bg_white)) }
                )
                FilterChip(
                    selected = background == 0xFF000000.toInt(),
                    onClick = { viewModel.setBackground(0xFF000000.toInt()) },
                    label = { Text(stringResource(R.string.design_bg_black)) }
                )
            }
        }

        Text(
            text = stringResource(R.string.design_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

@Composable
private fun TextDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_edit_text)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(200) },
                placeholder = { Text(stringResource(R.string.enter_text)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    onConfirm: (ExportFormat, Int) -> Unit
) {
    var format by remember { mutableStateOf(ExportFormat.PNG) }
    var quality by remember { mutableFloatStateOf(95f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photo_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.export_format),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.values().forEach { option ->
                        FilterChip(
                            selected = format == option,
                            onClick = { format = option },
                            label = { Text(option.name) }
                        )
                    }
                }
                if (format.supportsQuality) {
                    Text(
                        text = "${stringResource(R.string.export_quality)}: ${quality.roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 50f..100f
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(format, quality.roundToInt()) }) {
                Text(stringResource(R.string.photo_save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
