// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class C {
    private fun function(a: String, b: String = "", c: String): String {
        return a + b + c
    }

    fun foo() {
        //Breakpoint!
        val x = 1
    }
}

fun main() {
    C().foo()
}

// EXPRESSION: function(a = "O", c = "K")
// RESULT: "OK": Ljava/lang/String;