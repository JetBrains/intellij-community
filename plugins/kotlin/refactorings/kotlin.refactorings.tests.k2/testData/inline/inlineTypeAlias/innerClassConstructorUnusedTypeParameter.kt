class Outer {
    inner class Inner(val value: String)
}

typealias <caret>Alias<T> = Outer.Inner

fun use(outer: Outer) {
    outer.Alias<String>("one")
    outer.Alias("two")
}
