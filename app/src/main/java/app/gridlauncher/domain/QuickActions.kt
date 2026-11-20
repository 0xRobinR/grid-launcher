package app.gridlauncher.domain

/**
 * Decides which app shortcuts appear as quick-action buttons under an app in the grid.
 *
 * `pinned == null` means the user never customised this app, so the app's own top
 * shortcuts are used. Once the user pins or unpins anything, their explicit list is kept,
 * even when it is empty.
 */
object QuickActions {
    const val MAX_PER_APP = 2

    fun resolve(available: List<String>, pinned: List<String>?, count: Int): List<String> {
        val limit = count.coerceIn(0, MAX_PER_APP)
        if (limit == 0) return emptyList()
        if (pinned == null) return available.take(limit)
        return pinned.filter { it in available }.distinct().take(limit)
    }

    /** Returns the new pinned list after the user toggles [id]; the oldest pin drops when full. */
    fun togglePin(available: List<String>, pinned: List<String>?, count: Int, id: String): List<String> {
        val limit = count.coerceIn(1, MAX_PER_APP)
        val current = resolve(available, pinned, limit).toMutableList()
        if (id in current) {
            current.remove(id)
        } else if (id in available) {
            current.add(id)
            while (current.size > limit) current.removeAt(0)
        }
        return current
    }
}
