package com.pixelcraft.studio.design

/**
 * Holds the document, the selected layer and the undo/redo history.
 * It has no Android dependencies, so it is fully unit-testable.
 */
class DesignEditor(
    initial: DesignDocument,
    private val historyLimit: Int = 200
) {

    var document: DesignDocument = initial
        private set

    var selectedId: String? = null
        private set

    private val undoStack = ArrayDeque<DesignCommand>()
    private val redoStack = ArrayDeque<DesignCommand>()
    private var gestureStart: Layer? = null

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val selectedLayer: Layer?
        get() = selectedId?.let { document.layerById(it) }

    /** Runs a command and records it. Returns false when nothing changed. */
    fun execute(command: DesignCommand): Boolean {
        val result = command.execute(document)
        val inverse = result.inverse ?: return false
        document = result.document
        pushUndo(inverse)
        redoStack.clear()
        fixSelection()
        return true
    }

    fun undo(): Boolean {
        val inverse = undoStack.removeLastOrNull() ?: return false
        val result = inverse.execute(document)
        document = result.document
        result.inverse?.let { redoStack.addLast(it) }
        fixSelection()
        return true
    }

    fun redo(): Boolean {
        val inverse = redoStack.removeLastOrNull() ?: return false
        val result = inverse.execute(document)
        document = result.document
        result.inverse?.let { undoStack.addLast(it) }
        fixSelection()
        return true
    }

    fun select(id: String?) {
        selectedId = if (id != null && document.indexOf(id) >= 0) id else null
    }

    /** Selects the top-most layer under the point (or clears the selection). */
    fun selectAt(x: Float, y: Float) {
        select(document.hitTest(x, y)?.id)
    }

    fun addLayer(layer: Layer): Boolean {
        val changed = execute(AddLayerCommand(layer))
        if (changed) selectedId = layer.id
        return changed
    }

    fun deleteSelected(): Boolean {
        val id = selectedId ?: return false
        return execute(RemoveLayerCommand(id))
    }

    fun duplicateSelected(newId: String): Boolean {
        val layer = selectedLayer ?: return false
        val copy = layer.copy(
            id = newId,
            name = layer.name + " copy",
            transform = layer.transform.movedBy(DUPLICATE_OFFSET, DUPLICATE_OFFSET),
            locked = false
        )
        val changed = execute(AddLayerCommand(copy, document.indexOf(layer.id) + 1))
        if (changed) selectedId = newId
        return changed
    }

    fun bringSelectedForward(): Boolean {
        val id = selectedId ?: return false
        return execute(ReorderLayerCommand(id, document.indexOf(id) + 1))
    }

    fun sendSelectedBackward(): Boolean {
        val id = selectedId ?: return false
        return execute(ReorderLayerCommand(id, document.indexOf(id) - 1))
    }

    /** Edits the selected layer as one undo step. Refused when it is locked. */
    fun updateSelected(change: (Layer) -> Layer): Boolean {
        val layer = selectedLayer ?: return false
        if (layer.locked) return false
        return execute(UpdateLayerCommand(change(layer)))
    }

    fun toggleLockSelected(): Boolean {
        val layer = selectedLayer ?: return false
        return execute(UpdateLayerCommand(layer.copy(locked = !layer.locked)))
    }

    // Live gestures: many small updates become a single undo step.

    fun beginGesture(): Boolean {
        val layer = selectedLayer ?: return false
        if (layer.locked) return false
        gestureStart = layer
        return true
    }

    fun updateGesture(change: (Layer) -> Layer) {
        val start = gestureStart ?: return
        val current = document.layerById(start.id) ?: return
        val updated = change(current)
        document = document.copy(
            layers = document.layers.map { if (it.id == start.id) updated else it }
        )
    }

    fun endGesture() {
        val start = gestureStart ?: return
        gestureStart = null
        val current = document.layerById(start.id) ?: return
        if (current == start) return
        pushUndo(UpdateLayerCommand(start))
        redoStack.clear()
    }

    private fun pushUndo(inverse: DesignCommand) {
        undoStack.addLast(inverse)
        if (undoStack.size > historyLimit) undoStack.removeFirst()
    }

    private fun fixSelection() {
        val id = selectedId
        if (id != null && document.indexOf(id) < 0) selectedId = null
    }

    companion object {
        const val DUPLICATE_OFFSET = 24f
    }
}
