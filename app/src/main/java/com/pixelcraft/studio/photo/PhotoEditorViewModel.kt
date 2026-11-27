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

    /** Working copy (up to 3072 px), used as a safe fallback for export. */
    var fullSource by mutableStateOf<Bitmap?>(null)
        private set

    /** Small copy (up to 1280 px) used for fast on-screen previews. */
    var previewSource by mutableStateOf<Bitmap?>(null)
        private set

    var state by mutableStateOf(PhotoEditState())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    private var sourceUri: Uri? = null
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
                sourceUri = uri
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

    /**
     * Exports at the highest resolution memory allows. The original photo is
     * decoded again for export; if that fails (permission lost or not enough
     * memory) the 3072 px working copy is used instead.
     */
    fun export(
        resolver: ContentResolver,
        format: ExportFormat,
        quality: Int,
        onResult: (Result<Uri>) -> Unit
    ) {
        val working = fullSource ?: return
        val snapshot = state
        val uri = sourceUri

        viewModelScope.launch {
            isSaving = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    var original: Bitmap? = null
                    if (uri != null) {
                        original = try {
                            PhotoIO.loadForExport(resolver, uri)
                        } catch (e: Exception) {
                            null
                        } catch (e: OutOfMemoryError) {
                            null
                        }
                    }

                    var saved: Uri? = null
                    if (original != null) {
                        saved = try {
                            renderAndSave(resolver, original, snapshot, format, quality)
                        } catch (e: OutOfMemoryError) {
                            null
                        } finally {
                            original.recycle()
                        }
                    }

                    saved ?: renderAndSave(resolver, working, snapshot, format, quality)
                }
            }
            isSaving = false
            onResult(result)
        }
    }

    private fun renderAndSave(
        resolver: ContentResolver,
        input: Bitmap,
        editState: PhotoEditState,
        format: ExportFormat,
        quality: Int
    ): Uri {
        val rendered = PhotoRenderer.render(input, editState)
        try {
            return PhotoIO.save(resolver, rendered, format, quality, fileName())
        } finally {
            rendered.recycle()
        }
    }

    private fun fileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "PixelCraft_$stamp"
    }
}
