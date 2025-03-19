abstract class A {
    val a: Int = 0
        get() = field + 10
}

fun main() {
    with(object : A() {}) {
        //Breakpoint!
        println()
    }
}

// EXPRESSION: a
// RESULT: 10: I

// EXPRESSION: a_field
// RESULT: 0: I

// IGNORE_K2
