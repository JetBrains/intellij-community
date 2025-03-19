package inlineFunctionWithDefaultArguments

inline fun f(
    x: Int = 2,
    y: Int = 3
): Int {
    //Breakpoint!
    return x * y
}

inline fun g() {
    f()
}

fun main() {
    g()
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES

// EXPRESSION: x + y
// RESULT: 5: I
