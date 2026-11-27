package com.pixelcraft.studio.photo

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixelcraft.studio.R
import com.pixelcraft.studio.core.theme.StudioCanvasBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    isRtl: Boolean,
    onNavigateBack: () -> Unit,
    viewModel: PhotoEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val state = viewModel.state
    val previewSource = viewModel.previewSource

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }

    // Geometry (rotate / flip / crop) is baked into a small bitmap. Colour
    // adjustments are applied on the GPU by a ColorFilter, so dragging a
    // slider never re-renders the bitmap.
    val geometryState = remember(state) { state.copy(adjustments = PhotoAdjustments()) }
    val colorFilter = remember(state.adjustments) {
        if (state.adjustments.isDefault) {
            null
        } else {
            ColorFilter.colorMatrix(
                ComposeColorMatrix(state.adjustments.toColorMatrix().array)
            )
        }
    }

    // Zoom and pan are reset whenever the photo or its geometry changes.
    var zoom by remember(previewSource, geometryState) { mutableFloatStateOf(1f) }
    var offset by remember(previewSource, geometryState) { mutableStateOf(Offset.Zero) }

    val originalImage = remember(previewSource) { previewSource?.asImageBitmap() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.load(context.contentResolver, uri) {
                Toast.makeText(
                    context,
                    context.getString(R.string.photo_load_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val pickPhoto = {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(geometryState, previewSource) {
        preview = if (previewSource == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                PhotoRenderer.render(previewSource, geometryState).asImageBitmap()
            }
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.photo_editor_title)) },
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
                            enabled = viewModel.fullSource != null && !viewModel.isSaving
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
                if (viewModel.isSaving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(StudioCanvasBackdrop)
                        .pointerInput(previewSource, geometryState) {
                            detectTapGestures(
                                onDoubleTap = {
                                    zoom = 1f
                                    offset = Offset.Zero
                                },
                                onPress = {
                                    showOriginal = true
                                    tryAwaitRelease()
                                    showOriginal = false
                                }
                            )
                        }
                        .pointerInput(previewSource, geometryState) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val newScale = (zoom * gestureZoom).coerceIn(1f, 8f)
                                val maxX = size.width * (newScale - 1f) / 2f
                                val maxY = size.height * (newScale - 1f) / 2f
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                                zoom = newScale
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val shown = if (showOriginal) originalImage else preview
                    when {
                        viewModel.isLoading -> CircularProgressIndicator()
                        shown != null -> Image(
                            bitmap = shown,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            colorFilter = if (showOriginal) null else colorFilter,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        )
                        else -> EmptyState(onPick = { pickPhoto() })
                    }
                }

                if (viewModel.fullSource != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 3.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = tab == 0,
                                    onClick = { tab = 0 },
                                    label = { Text(stringResource(R.string.photo_tab_adjust)) }
                                )
                                FilterChip(
                                    selected = tab == 1,
                                    onClick = { tab = 1 },
                                    label = { Text(stringResource(R.string.photo_tab_transform)) }
                                )
                                FilterChip(
                                    selected = tab == 2,
                                    onClick = { tab = 2 },
                                    label = { Text(stringResource(R.string.photo_tab_crop)) }
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                when (tab) {
                                    0 -> AdjustPanel(
                                        state = state,
                                        onLive = { viewModel.previewChange(it) },
                                        onCommit = { viewModel.commit() },
                                        onReset = {
                                            viewModel.change(
                                                state.withAdjustments(PhotoAdjustments())
                                            )
                                        }
                                    )
                                    1 -> TransformPanel(
                                        state = state,
                                        onChange = { viewModel.change(it) }
                                    )
                                    else -> CropPanel(
                                        state = state,
                                        onChange = { viewModel.change(it) }
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.photo_hold_compare),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (showSaveDialog) {
            SaveDialog(
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
private fun EmptyState(onPick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AddPhotoAlternate,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = stringResource(R.string.photo_pick_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Button(onClick = onPick) {
            Text(stringResource(R.string.photo_pick_button))
        }
    }
}

@Composable
private fun AdjustPanel(
    state: PhotoEditState,
    onLive: (PhotoEditState) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit
) {
    val adjustments = state.adjustments

    AdjustSlider(
        label = stringResource(R.string.brightness),
        value = adjustments.brightness,
        onChange = { onLive(state.withAdjustments(adjustments.copy(brightness = it))) },
        onFinished = onCommit
    )
    AdjustSlider(
        label = stringResource(R.string.contrast),
        value = adjustments.contrast,
        onChange = { onLive(state.withAdjustments(adjustments.copy(contrast = it))) },
        onFinished = onCommit
    )
    AdjustSlider(
        label = stringResource(R.string.saturation),
        value = adjustments.saturation,
        onChange = { onLive(state.withAdjustments(adjustments.copy(saturation = it))) },
        onFinished = onCommit
    )
    TextButton(onClick = onReset, enabled = !adjustments.isDefault) {
        Text(stringResource(R.string.reset_adjustments))
    }
}

@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${(value * 100).roundToInt()}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = PhotoAdjustments.MIN..PhotoAdjustments.MAX
        )
    }
}

@Composable
private fun TransformPanel(
    state: PhotoEditState,
    onChange: (PhotoEditState) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { onChange(state.rotatedLeft()) },
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.rotate_left),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = { onChange(state.rotatedRight()) },
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.rotate_right),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { onChange(state.flippedHorizontally()) },
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.flip_h),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = { onChange(state.flippedVertically()) },
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.flip_v),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CropPanel(
    state: PhotoEditState,
    onChange: (PhotoEditState) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CropAspect.values().forEach { aspect ->
            val label = when (aspect) {
                CropAspect.ORIGINAL -> stringResource(R.string.crop_original)
                CropAspect.SQUARE -> stringResource(R.string.crop_square)
                CropAspect.PORTRAIT_4_5 -> stringResource(R.string.crop_portrait)
                CropAspect.WIDE_16_9 -> stringResource(R.string.crop_wide)
            }
            FilterChip(
                selected = state.cropAspect == aspect,
                onClick = { onChange(state.withCrop(aspect)) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun SaveDialog(
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
