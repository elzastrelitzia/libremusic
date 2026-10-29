package app.pulse.android.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsShareSelectionStateTest {
    private val lines = listOf(
        "a".repeat(40),
        "",
        "b".repeat(30),
        "c".repeat(100),
        "d".repeat(60),
        "e".repeat(60),
        "f".repeat(60),
        "g".repeat(151),
    )

    @Test
    fun `toggle adds then removes the same index`() {
        val once = toggleSelection(lines, emptySet(), 0)
        assertEquals(setOf(0), once)
        assertEquals(emptySet<Int>(), toggleSelection(lines, once, 0))
    }

    @Test
    fun `blank lines are never selectable`() {
        assertFalse(canAddLine(lines, emptySet(), 1))
        assertEquals(emptySet<Int>(), toggleSelection(lines, emptySet(), 1))
    }

    @Test
    fun `character count ignores separators and blanks`() {
        assertEquals(70, characterCount(lines, setOf(0, 2)))
        assertEquals(70, characterCount(lines, setOf(0, 1, 2)))
    }

    @Test
    fun `a selection landing exactly on 150 is allowed`() {
        val exact = listOf("a".repeat(100), "b".repeat(50))
        assertTrue(canAddLine(exact, setOf(0), 1))
        assertEquals(150, characterCount(exact, setOf(0, 1)))
    }

    @Test
    fun `one character over the budget is refused`() {
        val over = listOf("a".repeat(100), "b".repeat(51))
        assertFalse(canAddLine(over, setOf(0), 1))
        assertEquals(setOf(0), toggleSelection(over, setOf(0), 1))
    }

    @Test
    fun `a single line longer than the budget cannot be selected`() {
        assertFalse(canAddLine(lines, emptySet(), 7))
        assertEquals(emptySet<Int>(), toggleSelection(lines, emptySet(), 7))
    }

    @Test
    fun `the sixth line is refused even when characters allow it`() {
        val short = List(7) { "x" }
        val five = setOf(0, 1, 2, 3, 4)
        assertFalse(canAddLine(short, five, 5))
        assertEquals(five, toggleSelection(short, five, 5))
    }

    @Test
    fun `toggling twice is idempotent`() {
        assertEquals(
            emptySet<Int>(),
            toggleSelection(lines, toggleSelection(lines, emptySet(), 0), 0)
        )
    }

    @Test
    fun `out of range indexes are ignored`() {
        assertEquals(0, characterCount(lines, setOf(99)))
        assertFalse(canAddLine(lines, emptySet(), 99))
    }
}
