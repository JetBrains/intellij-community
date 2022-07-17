package inlineStackTraceWithAlso

class Foo

inline fun Foo.body() {
    val x2 = 2
    //Breakpoint!
    myAddChild()
}

inline fun Foo.myAddChild() {
    val x3 = 3
    //Breakpoint!
    println()
}

fun main() {
    Foo().apply {
        val x1 = 1
        //Breakpoint!
        body()
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES

// EXPRESSION: x1
// RESULT: 1: I

// EXPRESSION: x2
// RESULT: 2: I

// EXPRESSION: x3
// RESULT: 3: I
