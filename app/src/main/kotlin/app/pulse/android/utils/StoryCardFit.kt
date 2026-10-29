package app.pulse.android.utils

/** Smallest fraction of the base size the lyric text may shrink to before the
 *  card gives up and ellipsizes instead. */
private const val MIN_FIT_SCALE = 0.7f

/**
 * Size the lyric text should be drawn at so [neededHeight] fits [availableHeight].
 * Returns [baseSize] when it already fits, a proportionally smaller size when that
 * stays above the floor, and null when the caller must ellipsize instead.
 */
fun fittedLyricTextSize(baseSize: Float, neededHeight: Float, availableHeight: Float): Float? {
    if (neededHeight <= availableHeight) return baseSize
    if (availableHeight <= 0f) return null
    val fitted = baseSize * availableHeight / neededHeight
    return if (fitted < baseSize * MIN_FIT_SCALE) null else fitted
}
