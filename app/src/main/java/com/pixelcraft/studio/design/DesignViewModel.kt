package com.pixelcraft.studio.design

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelcraft.studio.photo.ExportFormat
import com.pixelcraft.studio.photo.PhotoIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.min

/** Bridges the pure [DesignEditor] to Compose state and project storage. */
class DesignViewModel : ViewModel() {

    private var editor = DesignEditor(blankDocument())

    var document by mutableStateOf(editor.document)
        private set

    var selectedId by mutableStateOf<String?>(null)
        private set

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var projectName by mutableStateOf(defaultName())
        private set

    /** Decoded bitmaps of image layers, keyed by [LayerContent.Image.sourceId]. */
    val images = HashMap<String, Bitmap>()

    val selectedLayer: Layer?
        get() = selectedId?.let { document.layerById(it) }

    private val measurer = DesignRenderer()
    private var layerCounter = 0
    private var editActive = false

    private var store: DesignProjectStore? = null
    private var projectId: String? = null
    private var autosaveJob: Job? = null
    private val saveMutex = Mutex()

    private fun sync(autosave: Boolean = true) {
        document = editor.document
        selectedId = editor.selectedId
        canUndo = editor.canUndo
        canRedo = editor.canRedo
        if (autosave) scheduleAutosave()
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun nextName(): String {
        layerCounter++
        return "Layer $layerCounter"
    }

    // ---- Projects ----

    /**
     * Opens a saved project, or starts a new blank one when [id] has no file.
     * Calling it again with the same id (for example after rotation) does nothing.
     */
    fun openProject(projectStore: DesignProjectStore, id: String) {
        store = projectStore
        if (projectId == id) return
        autosaveJob?.cancel()
        projectId = id

        viewModelScope.launch {
            isLoading = true
            val loaded = withContext(Dispatchers.IO) {
                try {
                    projectStore.load(id)
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
            }

            images.clear()
            editActive = false
            if (loaded != null) {
                editor = DesignEditor(loaded.document)
                images.putAll(loaded.images)
                projectName = loaded.name
                layerCounter = loaded.document.layers.size
            } else {
                editor = DesignEditor(blankDocument())
                projectName = defaultName()
                layerCounter = 0
            }
            sync(autosave = false)
            isLoading = false
        }
    }

    fun renameProject(newName: String) {
        val clean = newName.trim().take(60)
        if (clean.isNotEmpty() && clean != projectName) {
            projectName = clean
            scheduleAutosave()
        }
    }

    /** Writes the project to disk now (call when the screen goes to background). */
    fun saveNow() {
        val id = projectId ?: return
        val projectStore = store ?: return
        val snapshot = editor.document
        // Do not create files for projects the user never touched.
        if (snapshot.layers.isEmpty() && !projectStore.exists(id)) return

        val imageSnapshot = HashMap(images)
        val name = projectName
        viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                runCatching { projectStore.save(id, name, snapshot, imageSnapshot) }
            }
        }
    }

    private fun scheduleAutosave() {
        if (projectId == null || store == null) return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveNow()
        }
    }

    // ---- Adding layers ----

    fun addShape(kind: ShapeKind) {
        val doc = editor.document
        val (width, height) = when (kind) {
            ShapeKind.RECTANGLE -> 400f to 300f
            ShapeKind.ELLIPSE -> 360f to 360f
            ShapeKind.LINE -> 500f to 14f
        }
        editor.addLayer(
            Layer(
                id = newId(),
                name = nextName(),
                content = LayerContent.Shape(kind, fillArgb = 0xFF6366F1.toInt()),
                transform = LayerTransform(doc.width / 2f, doc.height / 2f, width, height)
            )
        )
        sync()
    }

    fun addText(text: String) {
        val doc = editor.document
        val content = LayerContent.Text(
            text = text,
            sizePx = doc.width * 0.08f,
            colorArgb = 0xFF111827.toInt(),
            bold = true
        )
        val width = doc.width * 0.7f
        val height = measurer.measureTextHeight(content, width.toInt()) + TEXT_PADDING
        editor.addLayer(
            Layer(
                id = newId(),
                name = nextName(),
                content = content,
                transform = LayerTransform(doc.width / 2f, doc.height / 2f, width, height)
            )
        )
        sync()
    }

    fun addImage(resolver: ContentResolver, uri: Uri, onError: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    PhotoIO.load(resolver, uri, IMAGE_MAX_SIDE)
                }
                val doc = editor.document
                val scale = min(
                    doc.width * 0.6f / bitmap.width,
                    doc.height * 0.6f / bitmap.height
                )
                val id = newId()
                images[id] = bitmap
                editor.addLayer(
                    Layer(
                        id = newId(),
                        name = nextName(),
                        content = LayerContent.Image(id),
                        transform = LayerTransform(
                            doc.width / 2f,
                            doc.height / 2f,
                            bitmap.width * scale,
                            bitmap.height * scale
                        )
                    )
                )
                sync()
            } catch (e: Exception) {
                onError()
            } catch (e: OutOfMemoryError) {
                onError()
            } finally {
                isLoading = false
            }
        }
    }

    // ---- Selection ----

    fun select(id: String?) {
        editor.select(id)
        sync(autosave = false)
    }

    // ---- Live edits (gestures and sliders): many updates, one undo step ----

    fun transformSelected(dx: Float, dy: Float, zoom: Float, rotationDeg: Float) {
        editLive { layer ->
            var t = layer.transform.movedBy(dx, dy)
            if (zoom != 1f) t = t.scaledBy(zoom)
            if (rotationDeg != 0f) t = t.rotatedBy(rotationDeg)
            layer.copy(transform = t)
        }
    }

    fun setOpacityLive(value: Float) {
        editLive { it.copy(opacity = value.coerceIn(0f, 1f)) }
    }

    fun editLive(change: (Layer) -> Layer) {
        if (!editActive) editActive = editor.beginGesture()
        if (editActive) {
            editor.updateGesture(change)
            sync()
        }
    }

    fun finishEdit() {
        if (editActive) {
            editor.endGesture()
            editActive = false
            sync()
        }
    }

    // ---- One-shot edits ----

    fun undo() {
        editor.undo()
        sync()
    }

    fun redo() {
        editor.redo()
        sync()
    }

    fun deleteSelected() {
        editor.deleteSelected()
        sync()
    }

    fun duplicateSelected() {
        editor.duplicateSelected(newId())
        sync()
    }

    fun bringForward() {
        editor.bringSelectedForward()
        sync()
    }

    fun sendBackward() {
        editor.sendSelectedBackward()
        sync()
    }

    fun toggleLock() {
        editor.toggleLockSelected()
        sync()
    }

    fun toggleVisible() {
        val layer = editor.selectedLayer ?: return
        // Visibility is allowed even when the layer is locked.
        editor.execute(UpdateLayerCommand(layer.copy(visible = !layer.visible)))
        sync()
    }

    fun setColor(argb: Int) {
        editor.updateSelected { layer ->
            when (val content = layer.content) {
                is LayerContent.Shape -> layer.copy(content = content.copy(fillArgb = argb))
                is LayerContent.Text -> layer.copy(content = content.copy(colorArgb = argb))
                is LayerContent.Image -> layer
            }
        }
        sync()
    }

    fun updateText(newText: String) {
        editor.updateSelected { layer ->
            val content = layer.content as? LayerContent.Text ?: return@updateSelected layer
            val updated = content.copy(text = newText)
            val height = measurer.measureTextHeight(updated, layer.transform.width.toInt()) + TEXT_PADDING
            layer.copy(
                content = updated,
                transform = layer.transform.copy(height = height)
            )
        }
        sync()
    }

    fun setBackground(argb: Int) {
        editor.execute(SetBackgroundCommand(argb))
        sync()
    }

    // ---- Export ----

    fun export(
        resolver: ContentResolver,
        format: ExportFormat,
        quality: Int,
        onResult: (Result<Uri>) -> Unit
    ) {
        val doc = editor.document
        val imageSnapshot = HashMap(images)

        viewModelScope.launch {
            isSaving = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
                    try {
                        DesignRenderer().draw(Canvas(bitmap), doc, imageSnapshot, showTransparencyGrid = false)
                        PhotoIO.save(resolver, bitmap, format, quality, fileName())
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            isSaving = false
            onResult(result)
        }
    }

    private fun fileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "PixelCraft_Design_$stamp"
    }

    private companion object {
        const val TEXT_PADDING = 24f
        const val IMAGE_MAX_SIDE = 2048
        const val AUTOSAVE_DELAY_MS = 1500L

        fun blankDocument() =
            DesignDocument(width = 1080, height = 1080, backgroundArgb = 0xFFFFFFFF.toInt())

        fun defaultName(): String {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            return "Design $stamp"
        }
    }
}
