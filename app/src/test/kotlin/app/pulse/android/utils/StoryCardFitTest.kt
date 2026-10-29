package app.pulse.android.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryCardFitTest {
    private val base = 66f

    @Test
    fun `text that already fits keeps its size`() {
        assertEquals(66f, fittedLyricTextSize(base, 200f, 400f)!!, 0.001f)
    }

    @Test
    fun `text shrinks proportionally while it stays above the floor`() {
        assertEquals(52.8f, fittedLyricTextSize(base, 500f, 400f)!!, 0.001f)
    }

    @Test
    fun `text that would need to go below the floor returns null`() {
        assertNull(fittedLyricTextSize(base, 1000f, 400f))
    }
}
