package test.pkg

class ArrayMap<K, V> : java.util.HashMap<K, V>()

inline fun <K, V> arrayMapOf(): ArrayMap<K, V> = ArrayMap()

fun <K, V> arrayMapOf(vararg pairs: Pair<K, V>): ArrayMap<K, V> {
    val map = ArrayMap<K, V>(pairs.size)
    for (pair in pairs) {
        map[pair.first] = pair.second
    }
    return map
}

fun <K, V> arrayMapOfNullable(vararg pairs: Pair<K, V>?): ArrayMap<K, V>? {
    return null
}