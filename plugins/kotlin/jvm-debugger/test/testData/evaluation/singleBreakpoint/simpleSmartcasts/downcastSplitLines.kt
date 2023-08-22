// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package downcastFail

fun main(args: Array<String>) {
    test1(Derived())
}

fun test1(derived: Base) =
    if (derived is Derived) {
        //Breakpoint!
        derived.prop == 1
    } else {
        false
    }

class Derived : Base() {
    val prop = 1
}

open class Base

// EXPRESSION: derived.prop
// RESULT: 1: I
