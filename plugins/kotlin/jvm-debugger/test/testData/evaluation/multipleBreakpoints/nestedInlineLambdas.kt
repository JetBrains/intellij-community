package nestedInlineLambdas

fun main() {
    val m = 2
    g(0) { xLambdaG, yLambdaG ->
        h()
        val p = 12
        //Breakpoint!
        println()
        j(4) { xLambdaJ, yLambdaJ ->
            val s = 22
            //Breakpoint!
            println()
        }
    }

    val m1 = 2
    g(0) { xLambdaG, yLambdaG ->
        h()
        val p1 = 12
        //Breakpoint!
        println()
        j(4) { xLambdaJ, yLambdaJ ->
            val s2 = 22
            //Breakpoint!
            println()
        }
    }
}

inline fun g(xg: Int, block: (Int, Int) -> Unit) {
    foo()
    block(1, 2)
    bar()
}

inline fun j(xj: Int, block: (Int, Int) -> Unit) {
    block(3, 4)
}

inline fun h() {
    val xh = 1
    val yh = 2
    i()
    //Breakpoint!
    println()
}

inline fun i() {
    val zi = 3
    //Breakpoint!
    println()
}

inline fun foo() {
    val qfoo = 2
    //Breakpoint!
    println()
}

inline fun bar() {
    val wbar = 2
    //Breakpoint!
    println()
}

// EXPRESSION: qfoo
// RESULT: 2: I

// EXPRESSION: zi
// RESULT: 3: I

// EXPRESSION: xh + yh
// RESULT: 3: I

// EXPRESSION: p + xLambdaG + yLambdaG + m
// RESULT: 17: I


// EXPRESSION: xLambdaJ + yLambdaJ + s + p + m + xLambdaG + yLambdaG
// RESULT: 46: I

// EXPRESSION: wbar
// RESULT: 2: I

// EXPRESSION: qfoo
// RESULT: 2: I

// EXPRESSION: zi
// RESULT: 3: I

// EXPRESSION: xh + yh
// RESULT: 3: I

// EXPRESSION: p1 + m + m1 + xLambdaG + yLambdaG
// RESULT: 19: I

// EXPRESSION: xLambdaJ + yLambdaJ + s2 + p1 + m1 + m + xLambdaG + yLambdaG
// RESULT: 48: I

// EXPRESSION: wbar
// RESULT: 2: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
