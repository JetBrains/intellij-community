// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package missingMember

interface I

class B : I {
    val p = 42
}

fun main(args: Array<String>) {
    val i: I = B()
    //Breakpoint!
    println(i)
}

// EXPRESSION: i.q
// RESULT: Unresolved reference 'q' on receiver of type 'I'.