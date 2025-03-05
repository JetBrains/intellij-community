// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// ATTACH_LIBRARY: utils
package breakpointsInLambdasWithDestructuring

import destructurableClasses.A

fun interface Runnable {
    fun run(a: A)
}

fun foo(f: (A) -> Unit) {
    f(A(1, 2))
}

fun run(r: Runnable) {
    r.run(A(1, 2))
}

inline fun inlineFoo(f: (A) -> Unit) {
    f(A(1, 2))
}

inline fun inlineRun(r: Runnable) {
    r.run(A(1, 2))
}

fun main() {
    //Breakpoint! (lambdaOrdinal = 1)
    foo { (x, y) -> println() }
    //Breakpoint! (lambdaOrdinal = 1)
    foo { (x, _) -> println() }
    //Breakpoint! (lambdaOrdinal = 1)
    inlineFoo { (x, y) -> println() }
    //Breakpoint! (lambdaOrdinal = 1)
    inlineFoo { (_, y) -> println() }

    //Breakpoint! (lambdaOrdinal = 1)
    run { (x, y) -> println() }
    //Breakpoint! (lambdaOrdinal = 1)
    inlineRun { (x, y) -> println() }

    val list = listOf(A(1, 2))
    //Breakpoint! (lambdaOrdinal = 1)
    list.filter { (x, y) -> x > y }
    //Breakpoint! (lambdaOrdinal = 1)
    list.forEach { (x, y) -> x + y }

    val map = mapOf(1 to 2)
    //Breakpoint! (lambdaOrdinal = 1)
    map.forEach { (key, value) -> println() }
}

// EXPRESSION: x + y
// RESULT: 'x' is not captured
// RESULT_K2: 3: I
// EXPRESSION: x
// RESULT: 'x' is not captured
// RESULT_K2: 1: I
// EXPRESSION: x + y
// RESULT: 'x' is not captured
// RESULT_K2: 3: I
// EXPRESSION: y
// RESULT: 'y' is not captured
// RESULT_K2: 2: I
// EXPRESSION: x + y
// RESULT: 'x' is not captured
// RESULT_K2: 3: I
// EXPRESSION: x + y
// RESULT: 'x' is not captured
// RESULT_K2: 3: I
// EXPRESSION: x + y
// RESULT: 'x' is not captured
// RESULT_K2: 3: I
// EXPRESSION: x + y
// RESULT: 'x' is not captured
// RESULT_K2: 3: I
// EXPRESSION: key + value
// RESULT: 'key' is not captured
// RESULT_K2: 3: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
