package app.pulse.core.data.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class QueueMergeTest {

    @Test
    fun appendsOnlyNewIds() {
        assertEquals(
            listOf("a", "b", "c"),
            mergeById(listOf("a", "b"), listOf("b", "c")) { it }
        )
    }

    @Test
    fun keepsExistingOrder() {
        // incoming order must not reorder the queue
        assertEquals(
            listOf("a", "b", "c", "d"),
            mergeById(listOf("a", "b"), listOf("c", "d")) { it }
        )
    }

    @Test
    fun dropsEntriesWithNullId() {
        // a null id must not dedupe everything together under one null key
        assertEquals(
            listOf("a", "b"),
            mergeById(listOf("a"), listOf(null, "b")) { it }
        )
    }

    @Test
    fun isEmptySafe() {
        assertEquals(listOf("x"), mergeById(emptyList(), listOf("x")) { it })
    }

    @Test
    fun bothEmpty() {
        assertEquals(emptyList(), mergeById(emptyList(), emptyList<String>()) { it })
    }
}
