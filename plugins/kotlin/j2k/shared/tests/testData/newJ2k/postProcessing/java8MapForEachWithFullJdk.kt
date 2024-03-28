import java.util.function.BiConsumer

internal class Test {
    fun test(map: HashMap<String?, String?>) {
        map.forEach { (key: String?, value: String?) -> foo(key, value) }
        map.forEach { (k: String?, v: String?) -> println("don't use params") }

        val biConsumer: BiConsumer<String?, String?> = MyBiConsumer()
        map.forEach(biConsumer)
    }

    fun foo(key: String?, value: String?) {
    }

    internal inner class MyBiConsumer : BiConsumer<String?, String?> {
        override fun accept(k: String?, v: String?) {
            println()
        }
    }
}
