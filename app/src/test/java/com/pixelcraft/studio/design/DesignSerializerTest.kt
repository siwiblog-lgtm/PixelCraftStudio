package com.pixelcraft.studio.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSerializerTest {

    private fun sampleDocument() = DesignDocument(
        width = 1080,
        height = 1920,
        backgroundArgb = 0xFFFFFFFF.toInt(),
        layers = listOf(
            Layer(
                id = "a",
                name = "Circle",
                content = LayerContent.Shape(
                    kind = ShapeKind.ELLIPSE,
                    fillArgb = 0xFF112233.toInt(),
                    strokeArgb = 0xFF000000.toInt(),
                    strokeWidth = 4f
                ),
                transform = LayerTransform(10f, 20f, 30f, 40f, 50f),
                opacity = 0.5f,
                visible = false,
                locked = true
            ),
            Layer(
                id = "b",
                name = "Title",
                content = LayerContent.Text("مرحبا Hello", 48f, 0xFFFF0000.toInt(), true),
                transform = LayerTransform(540f, 960f, 600f, 120f, 12.5f)
            ),
            Layer(
                id = "c",
                name = "Photo",
                content = LayerContent.Image("img-1"),
                transform = LayerTransform(100f, 100f, 400f, 300f)
            )
        )
    )

    private fun encodedSample(): String = DesignSerializer.encode(
        DesignProjectFile(name = "Test", updatedAtEpochMs = 123L, document = sampleDocument())
    )

    @Test
    fun roundTrip_keepsEverything() {
        val decoded = DesignSerializer.decode(encodedSample()).getOrThrow()

        assertEquals("Test", decoded.name)
        assertEquals(123L, decoded.updatedAtEpochMs)
        assertEquals(sampleDocument(), decoded.document)
    }

    @Test
    fun arabicAndEnglishText_surviveRoundTrip() {
        val decoded = DesignSerializer.decode(encodedSample()).getOrThrow()
        val text = decoded.document.layerById("b")!!.content as LayerContent.Text
        assertEquals("مرحبا Hello", text.text)
    }

    @Test
    fun corruptFile_failsInsteadOfCrashing() {
        assertTrue(DesignSerializer.decode("{not json").isFailure)
        assertTrue(DesignSerializer.decode("").isFailure)
    }

    @Test
    fun unknownFields_areIgnored() {
        val withExtra = encodedSample().replaceFirst("{", "{\"future\":42,")
        val decoded = DesignSerializer.decode(withExtra).getOrThrow()
        assertEquals(sampleDocument(), decoded.document)
    }

    @Test
    fun invalidCanvasSize_isRejected() {
        val broken = encodedSample().replaceFirst("\"width\":1080", "\"width\":0")
        assertTrue(DesignSerializer.decode(broken).isFailure)
    }
}
