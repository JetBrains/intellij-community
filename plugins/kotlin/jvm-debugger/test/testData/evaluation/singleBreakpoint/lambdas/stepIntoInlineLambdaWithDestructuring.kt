// ATTACH_LIBRARY: utils
package stepIntoInlineLambdaWithDestructuring

import destructurableClasses.A
import destructurableClasses.B

inline fun inlineFoo(f: (A, Int, Int, B, Int, A, B) -> Unit) {
    val a = A(1, 1)
    val b = B()
    // STEP_INTO: 1
    // EXPRESSION: a + b + c + d + e + f + g + h + i + j + k + l + m + n + o
    // RESULT: 15: I
    //Breakpoint!
    f(a, 1, 1, b, 1, a, b)
}

fun main() {
    inlineFoo { (a, b),
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
