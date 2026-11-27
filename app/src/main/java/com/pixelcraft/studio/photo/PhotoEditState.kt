package com.pixelcraft.studio.photo

/** Centered crop presets. ORIGINAL keeps the full image. */
enum class CropAspect(val ratio: Float?) {
    ORIGINAL(null),
    SQUARE(1f),
    PORTRAIT_4_5(4f / 5f),
    WIDE_16_9(16f / 9f)
}

/**
 * Immutable description of every edit applied to the source photo.
 * The original bitmap is never modified; the state is replayed on it.
 */
data class PhotoEditState(
    val rotationQuarterTurns: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropAspect: CropAspect = CropAspect.ORIGINAL,
    val adjustments: PhotoAdjustments = PhotoAdjustments()
) {
    val isDefault: Boolean
        get() = this == PhotoEditState()

    fun rotatedRight(): PhotoEditState =
        copy(rotationQuarterTurns = (rotationQuarterTurns + 1) and 3)

    fun rotatedLeft(): PhotoEditState =
        copy(rotationQuarterTurns = (rotationQuarterTurns + 3) and 3)

    fun flippedHorizontally(): PhotoEditState =
        copy(flipHorizontal = !flipHorizontal)

    fun flippedVertically(): PhotoEditState =
        copy(flipVertical = !flipVertical)

    fun withCrop(aspect: CropAspect): PhotoEditState =
        copy(cropAspect = aspect)

    fun withAdjustments(value: PhotoAdjustments): PhotoEditState =
        copy(adjustments = value)
}
