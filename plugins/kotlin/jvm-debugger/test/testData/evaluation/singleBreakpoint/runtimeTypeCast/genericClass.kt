// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package genericClass

interface I

class C<T>(val p: T) : I

fun main(args: Array<String>) {
    val m: I = C(42)
    //Breakpoint!
    println(m)
}

// EXPRESSION: m.p
// RESULT: 42: I

// EXPRESSION: m.p + 1
// RESULT: 43: I