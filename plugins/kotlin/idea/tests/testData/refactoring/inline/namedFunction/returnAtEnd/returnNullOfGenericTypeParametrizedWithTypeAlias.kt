typealias AliasedInt = Int

class GenericClass<T> {
    fun dec(): Int
}

fun <caret>a(): GenericClass<AliasedInt>? {
    return null
}

fun test() {
    val result: Int? = a()?.dec()
}