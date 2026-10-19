// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package objectMember

object O {
    fun f() = 1
}

class C {
    companion object {
        fun g() = 2
    }
}

fun main(args: Array<String>) {
    //Breakpoint!
    println(O.f() + C.g())
}

// EXPRESSION: O.f()
// RESULT: 1: I

// EXPRESSION: C.g()
// RESULT: 2: I
