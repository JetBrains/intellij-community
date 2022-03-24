package inlineStackTrace

inline fun fun1() {
    val x1 = 1
    // EXPRESSION: x1
    // RESULT: 1: I
    //Breakpoint!
    fun2()
}

inline fun fun2() {
    val x2 = 1
    // EXPRESSION: x2
    // RESULT: 1: I
    //Breakpoint!
    fun3()
    fun4()
}

inline fun fun3() {
    val x3 = 1
    // EXPRESSION: x3
    // RESULT: 1: I
    //Breakpoint!
    println()
}

inline fun fun4() {
    val x4 = 1
    // EXPRESSION: x4
    // RESULT: 1: I
    //Breakpoint!
    ordinaryFun1()
}

fun ordinaryFun1() {
    val y1 = 1
    // EXPRESSION: y1
    // RESULT: 1: I
    //Breakpoint!
    ordinaryFun2()
}

fun ordinaryFun2() {
    val y2 = 1
    // EXPRESSION: y2
    // RESULT: 1: I
    //Breakpoint!
    fun5()
}

inline fun fun5() {
    val x5 = 1
    // EXPRESSION: x5
    // RESULT: 1: I
    //Breakpoint!
    fun6()
}

inline fun fun6() {
    val x6 = 1
    // EXPRESSION: x6
    // RESULT: 1: I
    //Breakpoint!
    ordinaryFun3()
}

fun ordinaryFun3() {
    val y3 = 1
    // EXPRESSION: y3
    // RESULT: 1: I
    //Breakpoint!
    fun7()
}

inline fun fun7() {
    val x7 = 1
    // EXPRESSION: x7
    // RESULT: 1: I
    //Breakpoint!
    fun8()
}

inline fun fun8() {
    val x8 = 1
    // EXPRESSION: x8
    // RESULT: 1: I
    //Breakpoint!
    with("A") {
        val w1 = 1
        // EXPRESSION: w1
        // RESULT: 1: I
        //Breakpoint!
        with("B") {
            val w2 = 1
            // EXPRESSION: w2
            // RESULT: 1: I
            //Breakpoint!
            fun9()
        }
    }
}

inline fun fun9() {
    val x9 = 1
    // EXPRESSION: x9
    // RESULT: 1: I
    //Breakpoint!
    println()
}

fun main(args: Array<String>) {
    fun1()
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
