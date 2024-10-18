// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// ATTACH_LIBRARY: utils
package stepIntoInlineLambdaWithParameterDestructuring1

import destructurableClasses.A

inline fun inlineFoo(f: (A, Int, Int, A, Int, A, A, A, Int, Int) -> Unit) {
    val a = A(1, 1)
    // STEP_INTO: 1
    //Breakpoint!
    f(a, 1, 1, a, 1, a, a, a, 1, 1)
}

fun main() {
    inlineFoo { (a, b),
          c,
          d, (e,
              f),
          g,
          (h, i),
          (j,
              k),
                (l, m),
        n, o ->
        println()
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES

// EXPRESSION: a + b + c + d + e + f + g + h + i + j + k + l + m + n + o
// RESULT: Unresolved reference: a
// RESULT_K2: 15: I
