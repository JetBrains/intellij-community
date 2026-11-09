import java.util.Collections
import java.util.function.Consumer

internal class A {
    var field: MutableList<String?> = ArrayList<String?>()

    fun read(items: List<String?>) {
        println(items)
        val joined = java.lang.String.join(",", items)
        val copy: List<String?> = ArrayList<String?>(items)
    }

    fun shuffledByLibrary(items: MutableList<String?>) {
        Collections.shuffle(items)
    }

    fun storedInField(items: MutableList<String?>) {
        field = items
    }

    fun passedToMutatingParameter(items: MutableList<String>) {
        add(items)
    }

    fun add(items: MutableList<String>) {
        items.add("a")
    }

    fun entryChanged(map: MutableMap<String?, Int>) {
        for (entry in map.entries) {
            entry.setValue(1)
        }
    }

    fun keySetChanged(map: MutableMap<String?, Int?>) {
        map.keys.remove("a")
    }

    fun addedByMethodReference(items: MutableList<String?>, source: List<String?>) {
        source.forEach(Consumer { e: String? -> items.add(e) })
    }

    fun callback() {
        val callback = Callback { items: MutableList<String?>? -> items!!.add("a") }
    }
}

internal fun interface Callback {
    fun call(items: MutableList<String?>?)
}
