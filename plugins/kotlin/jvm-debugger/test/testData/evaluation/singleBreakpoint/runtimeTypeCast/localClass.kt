// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package localClass

interface I

fun main(args: Array<String>) {
    class A : I {
        val p = 7
    }

    val i: I = A()
    //Breakpoint!
    println(i)
}

// EXPRESSION: i.p
// RESULT: 7: I

// EXPRESSION: i.p + 1
// RESULT: 8: I