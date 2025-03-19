// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// ATTACH_LIBRARY: utils
package smartStepIntoLambdaWithParameterDestructuring

import destructurableClasses.A
import destructurableClasses.B

fun foo(f: (A, B, Int) -> Unit) {
    val a = A(1, 1)
    val b = B()
    f(a, b, 1)
}

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    val x = 1

    // SMART_STEP_INTO_BY_INDEX: 2
    foo { (a, b), (c, d, e, f), g -> println() }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES

// EXPRESSION: a + b + c + d + e + f + g
// RESULT: 'a' is not captured
// RESULT_K2: 7: I
