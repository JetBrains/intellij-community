class Outer {
    inner class Inner(val value: String)

    fun direct(): Inner = Alias("one")

    fun shadowed(): Inner {
        class Inner(val value: String)
        return Alias("two")
    }
}

typealias <caret>Alias = Outer.Inner

fun use(outer: Outer) {
    with(outer) { Alias("three") }
    with(outer) { with("other receiver") { Alias("four") } }
}
