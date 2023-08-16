package multipleInliningInAnonymousObject

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
            block()
            inlineCall {
                val g = 7
                //Breakpoint!
                println()
            }
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
    bar() {
        val d = 4
        //Breakpoint!
        println()
    }
}

// EXPRESSION: d
// RESULT: 4: I
// EXPRESSION: b + g + param
// RESULT: 15: I
// EXPRESSION: d
// RESULT: 4: I
// EXPRESSION: b + g + param
// RESULT: 15: I
// EXPRESSION: d
// RESULT: 4: I
// EXPRESSION: b + g + param
// RESULT: 15: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
