// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package methodCall

interface I

class B : I {
    fun f() = 42
    fun add(x: Int) = x + 1
}

fun main(args: Array<String>) {
    val i: I = B()
    //Breakpoint!
    println(i)
}

// EXPRESSION: i.f()
// RESULT: 42: I

// EXPRESSION: i.add(41)
// RESULT: 42: I
