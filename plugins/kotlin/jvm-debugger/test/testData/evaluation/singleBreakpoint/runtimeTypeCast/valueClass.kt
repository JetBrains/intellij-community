// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package valueClass

interface I

@JvmInline
value class V(val x: Int) : I

fun main(args: Array<String>) {
    val v: I = V(5)
    //Breakpoint!
    println(v)
}

// EXPRESSION: v.x
// RESULT: 5: I

// EXPRESSION: v.x + 1
// RESULT: 6: I
