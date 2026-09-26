package nestedInliningInAnonymousObjects

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

inline fun foo(crossinline block: () -> Unit) {
    bar {
        object {
            fun baz() {
                val x = 5
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
        }.baz()
    }
}

fun main() {
    foo {
        val y = 6
        //Breakpoint!
        println()
    }
}

// EXPRESSION: y
// RESULT: 6: I
// EXPRESSION: g + x
// RESULT: 12: I
// EXPRESSION: y
// RESULT: 6: I
// EXPRESSION: g + x
// RESULT: 12: I

// EXPRESSION: g + b
// RESULT: 9: I

// EXPRESSION: y
// RESULT: 6: I
// EXPRESSION: g + x
// RESULT: 12: I
// EXPRESSION: y
// RESULT: 6: I
// EXPRESSION: g + x
// RESULT: 12: I

// EXPRESSION: g + b
// RESULT: 9: I

// EXPRESSION: y
// RESULT: 6: I
// EXPRESSION: g + x
// RESULT: 12: I
// EXPRESSION: y
// RESULT: 6: I
// EXPRESSION: g + x
// RESULT: 12: I

// EXPRESSION: g + b
// RESULT: 9: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
