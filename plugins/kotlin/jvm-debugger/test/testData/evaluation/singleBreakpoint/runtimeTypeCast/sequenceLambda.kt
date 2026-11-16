// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package sequenceLambda

interface I

class B : I {
    val p = 42
}

fun main(args: Array<String>) {
    val seq = sequence {
        val s: I = B()
        yield(1)
        //Breakpoint!
        println(s)
        yield(2)
    }
    println(seq.toList())
}

// EXPRESSION: s.p
// RESULT: 42: I
