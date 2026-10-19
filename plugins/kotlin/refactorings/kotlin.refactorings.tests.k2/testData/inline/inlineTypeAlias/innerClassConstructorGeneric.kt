class Outer {
    inner class Inner<T>(val value: T)
}

typealias <caret>Alias<T> = Outer.Inner<T>

fun use(outer: Outer, nullableOuter: Outer?) {
    val typed: Alias<String> = outer.Alias("one")
    outer.Alias<String>("two")
    nullableOuter?.Alias("three")
}
