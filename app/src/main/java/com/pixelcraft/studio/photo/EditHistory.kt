package com.pixelcraft.studio.photo

/**
 * Undo/redo history for immutable states. Only small state objects are
 * stored, never bitmaps, so memory use stays low.
 */
class EditHistory<T>(initial: T, private val limit: Int = 100) {

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    var current: T = initial
        private set

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    /** Records a new state. Identical states are ignored. */
    fun push(newState: T) {
        if (newState == current) return
        undoStack.addLast(current)
        if (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
        current = newState
    }

    fun undo(): T {
        if (undoStack.isNotEmpty()) {
            redoStack.addLast(current)
            current = undoStack.removeLast()
        }
        return current
    }

    fun redo(): T {
        if (redoStack.isNotEmpty()) {
            undoStack.addLast(current)
            current = redoStack.removeLast()
        }
        return current
    }

    fun reset(initial: T) {
        undoStack.clear()
        redoStack.clear()
        current = initial
    }
}
