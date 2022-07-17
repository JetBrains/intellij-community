package inlineStackTraceWithComplexLambdas

inline fun baz() {
    val bazInt = 1
    //Breakpoint!
    println()
}

inline fun bar(f: () -> Unit)  {
    val barInt = 1
    //Breakpoint!
    f()
}

inline fun foo(f: () -> Unit)  {
    val fooInt = 1
    //Breakpoint!
    println()
    bar {
        val fooLambdaInt = 1
        //Breakpoint!
        f()
    }
}

fun main() {
    val mainInt = 1
    //Breakpoint!
    println()
    foo {
        val mainLambdaInt = 1
        //Breakpoint!
        baz()
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES

// EXPRESSION: mainInt
// RESULT: 1: I
// EXPRESSION: fooInt
// RESULT: 1: I
// EXPRESSION: barInt
// RESULT: 1: I
// EXPRESSION: fooLambdaInt
// RESULT: 1: I
// EXPRESSION: mainLambdaInt
// RESULT: 1: I
// EXPRESSION: bazInt
// RESULT: 1: I
