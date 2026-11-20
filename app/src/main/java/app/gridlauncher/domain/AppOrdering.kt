package app.gridlauncher.domain

import java.text.Collator

enum class SortMode {
    AZ,
    MOST_USED,
    CUSTOM;

    companion object {
        fun parse(value: String?): SortMode = entries.firstOrNull { it.name == value } ?: AZ
    }
}

/** Ordering rules for the app grid. Pure Kotlin so it can be unit tested on the JVM. */
object AppOrdering {

    fun defaultCollator(): Comparator<String> {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        return Comparator { a, b -> collator.compare(a, b) }
    }

    fun <T> sort(
        items: List<T>,
        key: (T) -> String,
        label: (T) -> String,
        mode: SortMode,
        launchCounts: Map<String, Int>,
        customOrder: List<String>,
        collator: Comparator<String> = defaultCollator(),
    ): List<T> {
        val alphabetical: Comparator<T> = compareBy(collator, label).thenBy(key)
        return when (mode) {
            SortMode.AZ -> items.sortedWith(alphabetical)
            SortMode.MOST_USED -> items.sortedWith(
                compareByDescending<T> { launchCounts[key(it)] ?: 0 }.then(alphabetical),
            )
            SortMode.CUSTOM -> {
                val position = HashMap<String, Int>(customOrder.size)
                customOrder.forEachIndexed { index, k -> position.putIfAbsent(k, index) }
                // Apps the user has not placed yet (new installs) go to the end, A to Z.
                items.sortedWith(
                    compareBy<T> { position[key(it)] ?: Int.MAX_VALUE }.then(alphabetical),
                )
            }
        }
    }

    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from == to || from !in list.indices || to !in list.indices) return list
        val result = list.toMutableList()
        val item = result.removeAt(from)
        result.add(to, item)
        return result
    }
}
