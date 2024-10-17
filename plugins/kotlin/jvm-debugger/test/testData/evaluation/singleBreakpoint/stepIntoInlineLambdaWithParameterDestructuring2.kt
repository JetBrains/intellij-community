// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// ATTACH_LIBRARY: utils
package stepIntoInlineLambdaWithParameterDestructuring2

import destructurableClasses.A

fun foo(a: A, block: (A, String, Int) -> Unit) {
    // STEP_INTO: 1
    //Breakpoint!
    block(a, "", 1)
}

fun main() {
    foo(A(1, 1)) {
            (x, _), _, w ->
        println()
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES

// EXPRESSION: x + w
// RESULT: Unresolved reference: x
// RESULT_K2: 2: I
