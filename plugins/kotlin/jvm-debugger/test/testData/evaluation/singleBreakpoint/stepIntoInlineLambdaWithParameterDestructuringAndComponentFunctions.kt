// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// ATTACH_LIBRARY: utils
package stepIntoInlineLambdaWithParameterDestructuringAndComponentFunctions

import destructurableClasses.A
import destructurableClasses.B

// Since line numbers from parameter destructuring are removed,
// when pressing 'step into' a user will jump into 'component'
// functions. To reach the lambda of interest all component function
// calls should be stepped over. This issue doesn't happen with dataclasses.

inline fun inlineFoo(f: (A, Int, Int, B, Int, A, B) -> Unit) {
    val a = A(1, 1)
    val b = B()
    // STEP_INTO: 17
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
// SHOW_KOTLIN_VARIABLES

// EXPRESSION: a + b + c + d + e + f + g + h + i + j + k + l + m + n + o
// RESULT: Unresolved reference: a
// RESULT_K2: 15: I
