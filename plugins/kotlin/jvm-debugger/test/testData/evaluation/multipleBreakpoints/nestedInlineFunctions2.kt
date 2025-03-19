package nestedInlineFunctions2

inline fun foo() {
    val xFoo = 1
    bar()
    //Breakpoint!
    println()
}

inline fun bar() {
    val xBar = 2
    baz()
    //Breakpoint!
    println()
}

inline fun baz() {
    val xBaz = 3
    //Breakpoint!
    println()
}

fun main() {
    foo()
    foo()
    bar()
    baz()
}

// EXPRESSION: xBaz
// RESULT: 3: I
// EXPRESSION: xBar
// RESULT: 2: I
// EXPRESSION: xFoo
// RESULT: 1: I

// EXPRESSION: xBaz
// RESULT: 3: I
// EXPRESSION: xBar
// RESULT: 2: I
// EXPRESSION: xFoo
// RESULT: 1: I

// EXPRESSION: xBaz
// RESULT: 3: I
// EXPRESSION: xBar
// RESULT: 2: I

// EXPRESSION: xBaz
// RESULT: 3: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
