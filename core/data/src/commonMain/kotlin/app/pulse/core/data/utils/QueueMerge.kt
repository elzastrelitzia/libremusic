package app.pulse.core.data.utils

/**
 * Append entries whose id is not already present, preserving existing order then incoming order.
 *
 * @param id extracts the identity of an entry, or null when the entry has none. Entries with a
 * null id are dropped: a null key would otherwise dedupe every id-less entry into one.
 */
fun <T> mergeById(existing: List<T>, incoming: List<T>, id: (T) -> String?): List<T> {
    // ponytail: O(n*m) if written with a list contains. Set keeps it O(n+m). Ceiling is a queue
    // of a few thousand songs; if that ever grows, the set is already the right shape.
    val seen = existing.mapNotNull(id).toHashSet()
    return existing + incoming.filter { val key = id(it); key != null && key !in seen }
}
