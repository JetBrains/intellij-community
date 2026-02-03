package inlineLambdasInAnonymousObjects

inline fun foo() {
    object {
        fun baz(param: Int) {
            val a = 1
            inlineCall {
                val f = 6
                //Breakpoint!
                println()
            }
        }
    }.baz(5)
}

inline fun bar(crossinline block: () -> Unit) {
    object {
        fun baz(param: Int) {
            val b = 2
            block()
            inlineCall {
                val g = 7
                //Breakpoint!
                println()
            }
        }
    }.baz(6)
}

inline fun inlineCall(block: () -> Unit) {
    block()
}

fun main() {
    foo()
    bar {
        val d = 4
        //Breakpoint!
        println()
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES

// EXPRESSION: a + f
// RESULT: 7: I

// EXPRESSION: d
// RESULT: 4: I

// EXPRESSION: b + g
// RESULT: 9: I
