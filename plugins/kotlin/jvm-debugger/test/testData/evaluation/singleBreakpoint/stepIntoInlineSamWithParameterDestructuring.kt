// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// ATTACH_LIBRARY: utils
package stepIntoInlineSamWithParameterDestructuring

import destructurableClasses.A
import destructurableClasses.B

fun interface Runnable {
    fun run(a: A, b: Int, c: Int, d: B, e: Int, f: A, g: B)
}

inline fun inlineRun(runnable: Runnable) {
    val a = A(1, 1)
    val b = B()
    // STEP_INTO: 1
    //Breakpoint!
    runnable.run(a, 1, 1, b, 1, a, b)
}

fun main() {
    inlineRun { (a, b),
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
// SHOW_KOTLIN_VARIABLES

// EXPRESSION: a + b + c + d + e + f + g + h + i + j + k + l + m + n + o
// RESULT: Unresolved reference: a
// RESULT_K2: 15: I
