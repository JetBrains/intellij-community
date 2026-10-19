// ERROR: The refactoring cannot select the receiver for this inner class

class Outer {
    inner class Inner(val value: String)
}

typealias <caret>Alias = Outer.Inner

fun use(outer: Outer) {
    with(outer) {
        with("other receiver") {
            class Inner(val value: String)
            Alias("value")
        }
    }
}
