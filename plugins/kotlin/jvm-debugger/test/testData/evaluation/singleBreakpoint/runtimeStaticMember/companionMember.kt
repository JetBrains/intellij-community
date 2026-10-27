// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package companionMember

interface I

class C : I {
    companion object {
        val c = 7
        fun g() = 9
    }
}

fun main(args: Array<String>) {
    val x: I = C()
    //Breakpoint!
    println(x)
}

// EXPRESSION: x.c
// RESULT: 7: I

// EXPRESSION: x.g()
// RESULT: 9: I
