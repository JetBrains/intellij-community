// ATTACH_LIBRARY: utils
package stepIntoSamLambdaWithDestructuring

import destructurableClasses.A
import destructurableClasses.B

fun interface Runnable {
    fun run(a: A, b: Int, c: Int, d: B, e: Int, f: A, g: B)
}

fun run(runnable: Runnable) {
    val a = A(1, 1)
    val b = B()
    // STEP_INTO: 1
    // EXPRESSION: a + b + c + d + e + f + g + h + i + j + k + l + m + n + o
    // RESULT: 15: I
    //Breakpoint!
    runnable.run(a, 1, 1, b, 1, a, b)
}

fun main() {
    run { (a, b),
          c,
          d, (e,
              f, g,
              h),
          i,
          (j, k),
          (l,
              m,
              n, o) ->
        println()
    }
}

// PRINT_FRAME
