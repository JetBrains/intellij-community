// "Suppress 'DIVISION_BY_ZERO' for initializer " "true"

fun foo() {
    var (a, b) = Pair<String, Int>("", 2 / <caret>0)
}

data class Pair<A, B>(val a: A, val b: B)
