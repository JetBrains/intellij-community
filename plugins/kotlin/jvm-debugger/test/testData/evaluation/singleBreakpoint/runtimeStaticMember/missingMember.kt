// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package missingMember

interface I

class C : I

fun main(args: Array<String>) {
    val x: I = C()
    //Breakpoint!
    println(x)
}

// EXPRESSION: x.q
// RESULT: Unresolved reference 'q' on receiver of type 'I'.
