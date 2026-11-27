package com.pixelcraft.studio.design

/**
 * Every edit is a command. Executing a command returns the new document and
 * the command that exactly undoes it, so history stores only small objects.
 */
interface DesignCommand {
    fun execute(document: DesignDocument): CommandResult
}

/** [inverse] is null when the command changed nothing. */
class CommandResult(val document: DesignDocument, val inverse: DesignCommand?)

class AddLayerCommand(
    private val layer: Layer,
    private val index: Int? = null
) : DesignCommand {
    override fun execute(document: DesignDocument): CommandResult {
        if (document.indexOf(layer.id) >= 0) return CommandResult(document, null)
        val position = (index ?: document.layers.size).coerceIn(0, document.layers.size)
        val updated = document.layers.toMutableList().apply { add(position, layer) }
        return CommandResult(document.copy(layers = updated), RemoveLayerCommand(layer.id))
    }
}

class RemoveLayerCommand(private val id: String) : DesignCommand {
    override fun execute(document: DesignDocument): CommandResult {
        val position = document.indexOf(id)
        if (position < 0) return CommandResult(document, null)
        val removed = document.layers[position]
        val updated = document.layers.toMutableList().apply { removeAt(position) }
        return CommandResult(
            document.copy(layers = updated),
            AddLayerCommand(removed, position)
        )
    }
}

/** Replaces the layer having the same id (move, resize, opacity, text, lock...). */
class UpdateLayerCommand(private val newLayer: Layer) : DesignCommand {
    override fun execute(document: DesignDocument): CommandResult {
        val position = document.indexOf(newLayer.id)
        if (position < 0) return CommandResult(document, null)
        val old = document.layers[position]
        if (old == newLayer) return CommandResult(document, null)
        val updated = document.layers.toMutableList().apply { set(position, newLayer) }
        return CommandResult(document.copy(layers = updated), UpdateLayerCommand(old))
    }
}

class ReorderLayerCommand(
    private val id: String,
    private val toIndex: Int
) : DesignCommand {
    override fun execute(document: DesignDocument): CommandResult {
        val from = document.indexOf(id)
        if (from < 0) return CommandResult(document, null)
        val target = toIndex.coerceIn(0, document.layers.lastIndex)
        if (from == target) return CommandResult(document, null)
        val updated = document.layers.toMutableList()
        val moved = updated.removeAt(from)
        updated.add(target, moved)
        return CommandResult(
            document.copy(layers = updated),
            ReorderLayerCommand(id, from)
        )
    }
}

class SetBackgroundCommand(private val argb: Int) : DesignCommand {
    override fun execute(document: DesignDocument): CommandResult {
        if (document.backgroundArgb == argb) return CommandResult(document, null)
        return CommandResult(
            document.copy(backgroundArgb = argb),
            SetBackgroundCommand(document.backgroundArgb)
        )
    }
}

/** Runs several commands as one undo step (used later for merge layers). */
class CompositeCommand(private val commands: List<DesignCommand>) : DesignCommand {
    override fun execute(document: DesignDocument): CommandResult {
        var current = document
        val inverses = ArrayList<DesignCommand>()
        for (command in commands) {
            val result = command.execute(current)
            current = result.document
            result.inverse?.let { inverses.add(it) }
        }
        if (inverses.isEmpty()) return CommandResult(document, null)
        return CommandResult(current, CompositeCommand(inverses.reversed()))
    }
}
