import java.util.function.Consumer

internal class Test {
    fun context() {
        val items: MutableList<Double?> = ArrayList<Double?>()
        items.add(1.0)
        items.forEach(Consumer { x: Double? -> println(x) })
    }
}
