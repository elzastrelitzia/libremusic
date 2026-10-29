package app.pulse.android.ui.screens.player

const val MAX_SELECTION_CHARACTERS = 150
const val MAX_SELECTION_LINES = 5

fun characterCount(lines: List<String>, selection: Set<Int>): Int =
    selection.sumOf { index -> lines.getOrNull(index)?.length ?: 0 }

fun canAddLine(lines: List<String>, selection: Set<Int>, index: Int): Boolean {
    val line = lines.getOrNull(index) ?: return false
    if (line.isBlank() || index in selection) return false
    if (selection.size >= MAX_SELECTION_LINES) return false
    return characterCount(lines, selection) + line.length <= MAX_SELECTION_CHARACTERS
}

fun toggleSelection(lines: List<String>, selection: Set<Int>, index: Int): Set<Int> =
    if (index in selection) selection - index
    else if (canAddLine(lines, selection, index)) selection + index
    else selection
