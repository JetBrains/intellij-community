// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package smartStepIntoInlineLambdasInFunctionsWithSameName

fun foo(x: Int) {
    val l = listOf(1)

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    l.forEach {
        println(it)
        l.forEach {
            println(it)
        }
    }
}

suspend fun foo(x: Int, y: Int) {
    val l = listOf(1)

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    l.forEach {
        println(it)
        l.forEach {
            println(it)
        }
    }
}

suspend fun main() {
    foo(1)
    foo(1, 2)
}

// IGNORE_K2
