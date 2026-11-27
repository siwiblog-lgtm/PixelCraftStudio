package com.pixelcraft.studio.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Holds the photo and edit history so they survive screen rotation.
 * Bitmaps are never edited in place: [PhotoEditState] is replayed on them.
 */
class PhotoEditorViewModel : ViewModel() {

    var fullSource by mutableStateOf<Bitmap?>(null)
        private set

    var previewSource by mutableStateOf<Bitmap?>(null)
        private set

    var state by mutableStateOf(PhotoEditState())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    private var historyVersion by mutableIntStateOf(0)
    private val history = EditHistory(PhotoEditState())

    val canUndo: Boolean
        get() = historyVersion >= 0 && history.canUndo

    val canRedo: Boolean
        get() = historyVersion >= 0 && history.canRedo

    fun load(resolver: ContentResolver, uri: Uri, onError: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val full = PhotoIO.load(resolver, uri)
                    full to PhotoIO.createPreview(full)
                }
                fullSource = loaded.first
                previewSource = loaded.second
                history.reset(PhotoEditState())
                state = PhotoEditState()
                historyVersion++
            } catch (e: Exception) {
                onError()
            } catch (e: OutOfMemoryError) {
                onError()
            } finally {
                isLoading = false
            }
        }
    }

    /** Updates the preview while a slider is being dragged (no history entry). */
    fun previewChange(newState: PhotoEditState) {
        state = newState
    }

    /** Records the current state in the history (call when a drag ends). */
    fun commit() {
        history.push(state)
        historyVersion++
    }

    /** Applies a one-shot change such as rotate, flip or crop. */
    fun change(newState: PhotoEditState) {
        state = newState
        commit()
    }

    fun undo() {
        state = history.undo()
        historyVersion++
    }

    fun redo() {
        state = history.redo()
        historyVersion++
    }

    fun export(
        resolver: ContentResolver,
        format: ExportFormat,
        quality: Int,
        onResult: (Result<Uri>) -> Unit
    ) {
        val source = fullSource ?: return
        val snapshot = state
        viewModelScope.launch {
            isSaving = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val rendered = PhotoRenderer.render(source, snapshot)
                    try {
                        PhotoIO.save(resolver, rendered, format, quality, fileName())
                    } finally {
                        rendered.recycle()
                    }
                }
            }
            isSaving = false
            onResult(result)
        }
    }

    private fun fileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "PixelCraft_$stamp"
    }
}
