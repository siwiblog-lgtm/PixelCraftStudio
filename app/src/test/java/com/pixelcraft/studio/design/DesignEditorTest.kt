package com.pixelcraft.studio.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignEditorTest {

    private fun shape(id: String, x: Float = 100f, y: Float = 100f) = Layer(
        id = id,
        name = id,
        content = LayerContent.Shape(ShapeKind.RECTANGLE),
        transform = LayerTransform(x, y, 100f, 50f)
    )

    private fun newEditor() = DesignEditor(DesignDocument(width = 1080, height = 1080))

    private fun ids(editor: DesignEditor) = editor.document.layers.map { it.id }

    @Test
    fun addLayer_selectsItAndSupportsUndoRedo() {
        val editor = newEditor()

        assertTrue(editor.addLayer(shape("a")))
        assertEquals("a", editor.selectedId)
        assertEquals(1, editor.document.layers.size)

        assertTrue(editor.undo())
        assertEquals(0, editor.document.layers.size)
        assertNull(editor.selectedId)

        assertTrue(editor.redo())
        assertEquals(1, editor.document.layers.size)
    }

    @Test
    fun removeLayer_undoRestoresOriginalPosition() {
        val editor = newEditor()
        editor.addLayer(shape("a"))
        editor.addLayer(shape("b"))
        editor.addLayer(shape("c"))

        assertTrue(editor.execute(RemoveLayerCommand("b")))
        assertEquals(listOf("a", "c"), ids(editor))

        assertTrue(editor.undo())
        assertEquals(listOf("a", "b", "c"), ids(editor))
    }

    @Test
    fun removingMissingLayer_changesNothingAndAddsNoHistory() {
        val editor = newEditor()
        assertFalse(editor.execute(RemoveLayerCommand("ghost")))
        assertFalse(editor.canUndo)
    }

    @Test
    fun reorder_movesLayerAndUndoReverts() {
        val editor = newEditor()
        editor.addLayer(shape("a"))
        editor.addLayer(shape("b"))
        editor.addLayer(shape("c"))

        editor.select("a")
        assertTrue(editor.bringSelectedForward())
        assertEquals(listOf("b", "a", "c"), ids(editor))

        assertTrue(editor.undo())
        assertEquals(listOf("a", "b", "c"), ids(editor))
    }

    @Test
    fun topLayer_cannotMoveForwardFurther() {
        val editor = newEditor()
        editor.addLayer(shape("a"))
        editor.addLayer(shape("b"))
        assertFalse(editor.bringSelectedForward())
    }

    @Test
    fun updateSelected_changesOpacityAndUndoRestoresIt() {
        val editor = newEditor()
        editor.addLayer(shape("a"))

        assertTrue(editor.updateSelected { it.copy(opacity = 0.4f) })
        assertEquals(0.4f, editor.selectedLayer!!.opacity, 0.0001f)

        assertTrue(editor.undo())
        assertEquals(1f, editor.document.layerById("a")!!.opacity, 0.0001f)
    }

    @Test
    fun lockedLayer_rejectsEditsAndGestures() {
        val editor = newEditor()
        editor.addLayer(shape("a"))
        assertTrue(editor.toggleLockSelected())

        assertFalse(editor.updateSelected { it.copy(opacity = 0.1f) })
        assertFalse(editor.beginGesture())

        assertTrue(editor.toggleLockSelected())
        assertTrue(editor.updateSelected { it.copy(opacity = 0.1f) })
    }

    @Test
    fun gesture_manyUpdatesBecomeOneUndoStep() {
        val editor = newEditor()
        editor.addLayer(shape("a", x = 100f, y = 100f))

        assertTrue(editor.beginGesture())
        editor.updateGesture { it.copy(transform = it.transform.movedBy(10f, 0f)) }
        editor.updateGesture { it.copy(transform = it.transform.movedBy(10f, 0f)) }
        editor.updateGesture { it.copy(transform = it.transform.movedBy(10f, 5f)) }
        editor.endGesture()

        assertEquals(130f, editor.selectedLayer!!.transform.centerX, 0.0001f)
        assertEquals(105f, editor.selectedLayer!!.transform.centerY, 0.0001f)

        assertTrue(editor.undo())
        assertEquals(100f, editor.selectedLayer!!.transform.centerX, 0.0001f)
        assertEquals(100f, editor.selectedLayer!!.transform.centerY, 0.0001f)

        assertTrue(editor.redo())
        assertEquals(130f, editor.selectedLayer!!.transform.centerX, 0.0001f)
    }

    @Test
    fun gesture_withoutChange_addsNoHistory() {
        val editor = newEditor()
        editor.addLayer(shape("a"))

        // Begin and end a gesture without changing the layer.
        assertTrue(editor.beginGesture())
        editor.endGesture()

        // Only the "add" is in history, so one undo empties it.
        assertTrue(editor.undo())
        assertFalse(editor.canUndo)
        assertEquals(0, editor.document.layers.size)
    }

    @Test
    fun newCommand_clearsRedoHistory() {
        val editor = newEditor()
        editor.addLayer(shape("a"))
        editor.undo()
        assertTrue(editor.canRedo)

        editor.addLayer(shape("b"))
        assertFalse(editor.canRedo)
    }

    @Test
    fun history_isLimitedToConfiguredSize() {
        val editor = DesignEditor(DesignDocument(100, 100), historyLimit = 3)
        for (i in 1..5) editor.addLayer(shape("l$i"))

        assertTrue(editor.undo())
        assertTrue(editor.undo())
        assertTrue(editor.undo())
        assertFalse(editor.undo())
        assertEquals(2, editor.document.layers.size)
    }

    @Test
    fun compositeCommand_isOneUndoStep() {
        val editor = newEditor()
        editor.addLayer(shape("a"))
        editor.addLayer(shape("b"))

        val merge = CompositeCommand(
            listOf(RemoveLayerCommand("a"), RemoveLayerCommand("b"), AddLayerCommand(shape("merged")))
        )
        assertTrue(editor.execute(merge))
        assertEquals(listOf("merged"), ids(editor))

        assertTrue(editor.undo())
        assertEquals(listOf("a", "b"), ids(editor))
    }

    @Test
    fun duplicate_insertsCopyAboveOriginalAndSelectsIt() {
        val editor = newEditor()
        editor.addLayer(shape("a"))
        editor.addLayer(shape("b"))
        editor.select("a")

        assertTrue(editor.duplicateSelected("a2"))
        assertEquals(listOf("a", "a2", "b"), ids(editor))
        assertEquals("a2", editor.selectedId)
        assertEquals(
            100f + DesignEditor.DUPLICATE_OFFSET,
            editor.selectedLayer!!.transform.centerX,
            0.0001f
        )
    }

    @Test
    fun hitTest_returnsTopMostVisibleLayer() {
        val editor = newEditor()
        editor.addLayer(shape("bottom"))
        editor.addLayer(shape("top"))

        assertEquals("top", editor.document.hitTest(100f, 100f)?.id)

        editor.select("top")
        editor.updateSelected { it.copy(visible = false) }
        assertEquals("bottom", editor.document.hitTest(100f, 100f)?.id)

        assertNull(editor.document.hitTest(900f, 900f))
    }

    @Test
    fun hitTest_respectsRotation() {
        // A wide, flat layer rotated 90 degrees becomes tall and narrow.
        val transform = LayerTransform(
            centerX = 100f, centerY = 100f, width = 100f, height = 20f, rotationDeg = 90f
        )
        assertTrue(transform.containsPoint(100f, 140f))
        assertFalse(transform.containsPoint(140f, 100f))
    }

    @Test
    fun scaledBy_neverShrinksBelowMinimumSize() {
        val transform = LayerTransform(0f, 0f, 100f, 50f).scaledBy(0.001f)
        assertTrue(transform.height >= LayerTransform.MIN_SIZE - 0.0001f)
        assertNotNull(transform)
    }

    @Test
    fun rotatedBy_wrapsAroundFullTurn() {
        val transform = LayerTransform(0f, 0f, 10f, 10f, 350f).rotatedBy(20f)
        assertEquals(10f, transform.rotationDeg, 0.0001f)
    }
}
