package inlineLambdaInInlineFunction

inline fun foo(xFoo: Int, f: (Int, Int) -> Unit) {
    val yFoo = 1
    f(xFoo, yFoo)
    //Breakpoint!
    println()
}

inline fun bar(xBar: Int) {
    val yBar = 2
    val dangerous = 3; foo(3) { x, y ->
        //Breakpoint!
        println()
    }
}

fun main() {
    bar(1)
    bar(2)
}

// EXPRESSION: x + y + dangerous + yBar + xBar
// RESULT: 10: I
// EXPRESSION: xFoo + yFoo
// RESULT: 4: I

// EXPRESSION: x + y + dangerous + yBar + xBar
// RESULT: 11: I
// EXPRESSION: xFoo + yFoo
// RESULT: 4: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
