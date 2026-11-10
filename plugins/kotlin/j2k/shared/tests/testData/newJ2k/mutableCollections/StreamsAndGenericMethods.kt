// ERROR: Initializer type mismatch: expected 'MutableList<String>', actual 'MutableList<String?>?'.
import java.util.stream.Collectors

internal class A {
    fun collectedAndChanged(items: List<String?>) {
        val copy: MutableList<String> = items.stream().collect(Collectors.toList())
        copy.add("a")
    }

    fun changedThroughGenericMethod(items: MutableList<String?>?) {
        val same: MutableList<String> = identity<MutableList<String?>?>(items)
        same.add("a")
    }

    fun <T> identity(value: T?): T? {
        return value
    }

    fun changedThroughGenericParameter(items: MutableList<String?>) {
        addTo<String?>(items, "a")
    }

    fun <T> addTo(list: MutableList<T?>, value: T?) {
        list.add(value)
    }
}
