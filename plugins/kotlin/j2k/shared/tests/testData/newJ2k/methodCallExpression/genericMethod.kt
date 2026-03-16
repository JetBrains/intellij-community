package demo

internal class Map {
    fun <K, V> put(k: K?, v: V?) {}
}

internal class U {
    fun test() {
        val m: Map = demo.Map()
        m.put<String?, Int?>(null, 10)
    }
}
