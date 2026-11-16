// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package thisReceiver

interface I

open class Base : I {
    fun show() {
        //Breakpoint!
        println("show")
    }
}

class Derived : Base() {
    val p = 21
}

fun main(args: Array<String>) {
    Derived().show()
}

// EXPRESSION: this.p
// RESULT: 21: I
