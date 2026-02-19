typealias AliasedInt = Int

fun <caret>a(): AliasedInt? {
    return null
}

fun test() {
    val result: Int? = a()?.dec()
}