import java.util.function.Consumer

internal class A {
    fun entryThroughIterator(map: MutableMap<String?, Int>) {
        map.entries.iterator().next().setValue(1)
    }

    fun entryThroughLambda(map: MutableMap<String?, Int?>) {
        map.entries.forEach(Consumer { e: MutableMap.MutableEntry<String?, Int?>? -> e!!.setValue(1) })
    }

    fun entryReadThroughLambda(map: Map<String?, Int?>) {
        map.entries.forEach(Consumer { e: Map.Entry<String?, Int?>? -> println(e!!.value) })
    }

    fun entryThroughLoop(map: MutableMap<String?, Int>) {
        for (entry in map.entries) {
            entry.setValue(1)
        }
    }

    fun innerListChanged(lists: List<MutableList<String>>) {
        for (list in lists) {
            list.add("a")
        }
    }

    fun innerListRead(lists: List<List<String?>>) {
        for (list in lists) {
            println(list.size)
        }
    }

    fun innerListChangedThroughGet(map: Map<String?, MutableList<String>?>) {
        map.get("a")!!.add("b")
    }
}
