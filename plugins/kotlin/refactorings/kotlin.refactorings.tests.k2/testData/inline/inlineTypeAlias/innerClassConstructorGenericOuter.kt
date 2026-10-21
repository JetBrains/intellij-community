class Outer<T>(val value: T) {
    inner class Inner<U>(val other: U)

    fun create() = Alias(1)
}

typealias <caret>Alias<T, U> = Outer<T>.Inner<U>

fun use(outer: Outer<String>) {
    outer.Alias<String, Int>(2)
    outer.Alias(3)
    val typed: Alias<String, Int> = outer.Alias(4)
    println(typed)
}
