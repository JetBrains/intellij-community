package nestedInlineLambdasAndVariablesWithSameName

inline fun foo(block: () -> Unit) {
    block()
}

fun main() {
    val x = 1
    val y = 1
    val z = 1
    foo {
        val x = 2
        val y = 2
        val z = 2
        foo {
            val x = 3
            val y = 3
            val z = 3
            foo {
                //Breakpoint!
                println()
            }
            //Breakpoint!
            println()
        }
        foo {
            //Breakpoint!
            println()
        }
        //Breakpoint!
        println()
    }
    foo {
        //Breakpoint!
        println()
    }
}

// EXPRESSION: x + y + z
// RESULT: 9: I

// EXPRESSION: x + y + z
// RESULT: 9: I

// EXPRESSION: x + y + z
// RESULT: 6: I

// EXPRESSION: x + y + z
// RESULT: 6: I

// EXPRESSION: x + y + z
// RESULT: 3: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
