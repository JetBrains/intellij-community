package privateToplevelField

private val f = 1

fun main() {
    //Breakpoint!
    println()
}

// From KT-341572

// EXPRESSION: f
// RESULT: 1: I