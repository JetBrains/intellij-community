class Outer {
    inner class Inner(val value: Int = 0)
}

typealias <caret>MyInnerAlias = Outer.Inner

fun main() {
    Outer().MyInnerAlias()
    Outer().MyInnerAlias(42)
}
