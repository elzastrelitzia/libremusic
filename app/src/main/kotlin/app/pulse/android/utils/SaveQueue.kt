package app.pulse.android.utils

class SaveQueue {
    private val keys = mutableSetOf<String>()

    fun offer(key: String): Boolean = synchronized(keys) { keys.add(key) }

    fun remove(key: String) = synchronized(keys) { keys.remove(key) }

    fun clear() = synchronized(keys) { keys.clear() }

    fun contains(key: String): Boolean = synchronized(keys) { keys.contains(key) }
}