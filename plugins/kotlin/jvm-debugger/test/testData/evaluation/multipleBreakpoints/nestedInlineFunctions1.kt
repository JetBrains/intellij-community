package nestedInlineFunctions

inline fun foo() {
    val fooVar = 100
    x1()
    x4()
    //Breakpoint!
    println()
}

inline fun x1() {
    val y1 = 1
    x2()
    x3()
    //Breakpoint!
    println()
}

inline fun x2() {
    val y2 = 2
    //Breakpoint!
    println()
}

inline fun x3() {
    val y3 = 3
    //Breakpoint!
    println()
}

inline fun x4() {
    val y4 = 4
    x5()
    x6()
    //Breakpoint!
    println()
}

inline fun x5() {
    val y5 = 5
    //Breakpoint!
    println()
}

inline fun x6() {
    val y6 = 6
    //Breakpoint!
    println()
}

fun main() {
    foo()
    foo()
}

// EXPRESSION: y2
// RESULT: 2: I
// EXPRESSION: y3
// RESULT: 3: I
// EXPRESSION: y1
// RESULT: 1: I
// EXPRESSION: y5
// RESULT: 5: I
// EXPRESSION: y6
// RESULT: 6: I
// EXPRESSION: y4
// RESULT: 4: I
// EXPRESSION: fooVar
// RESULT: 100: I

// EXPRESSION: y2
// RESULT: 2: I
// EXPRESSION: y3
// RESULT: 3: I
// EXPRESSION: y1
// RESULT: 1: I
// EXPRESSION: y5
// RESULT: 5: I
// EXPRESSION: y6
// RESULT: 6: I
// EXPRESSION: y4
// RESULT: 4: I
// EXPRESSION: fooVar
// RESULT: 100: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
