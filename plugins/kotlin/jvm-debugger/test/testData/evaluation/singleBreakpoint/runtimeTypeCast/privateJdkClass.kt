// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package privateJdkClass

fun main(args: Array<String>) {
    val single: Any = listOf(1)
    val pair: Any = listOf(1, 2)
    //Breakpoint!
    println("$single $pair")
}

// EXPRESSION: single.size
// RESULT: 1: I

// EXPRESSION: pair.size
// RESULT: 2: I
