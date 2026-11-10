// ERROR: Return type mismatch: expected 'MutableList<String>?', actual 'ArrayList<String?>'.
import java.util.function.Consumer

internal class A {
    fun computeIfAbsentChanged(map: MutableMap<String, MutableList<String>?>) {
        map.computeIfAbsent("k") { key: String? -> ArrayList<String?>() }!!.add("v")
    }

    fun valuesChangedThroughLambda(map: Map<String?, MutableList<String?>?>) {
        map.values.forEach(Consumer { list: MutableList<String?>? -> list!!.add("v") })
    }

    fun valuesChangedThroughLoop(map: Map<String?, MutableList<String?>>) {
        for (list in map.values) {
            list.clear()
        }
    }

    fun valuesRead(map: Map<String?, List<String?>>): Int {
        var size = 0
        for (list in map.values) {
            size += list.size
        }
        return size
    }

    fun entryValueChanged(map: Map<String?, MutableList<String>?>) {
        for (entry in map.entries) {
            entry.value!!.add("v")
        }
    }
}
