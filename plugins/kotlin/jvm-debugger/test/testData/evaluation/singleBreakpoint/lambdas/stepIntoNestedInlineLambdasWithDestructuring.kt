// ATTACH_LIBRARY: utils
package stepIntoNesterInlineLambdasWithDestructuring

import destructurableClasses.B

inline fun inlineFoo(f: (B) -> Unit) {
    val b = B()
    f(b)
}

inline fun inlineBar(f: (B) -> Unit) {
    val b = B()
    // STEP_INTO: 1
    // EXPRESSION: x + y
    // RESULT: 2: I
    //Breakpoint!
    f(b)
}

fun main() {
    inlineFoo { (x, _, y, _) ->
        inlineFoo { (x, _, y, _) ->
            println()
        }

        inlineFoo { (x, _, y, _) ->
            inlineFoo { (x, _, y, _) ->
                inlineBar { (x, _, y, _) -> println() }
            }
        }

        inlineFoo { (x, _, y, _) ->
            println()
        }
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
