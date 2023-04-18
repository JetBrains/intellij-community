// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// ATTACH_LIBRARY: utils
package stepIntoAnonymousObjectInsideLambdaWithDestructuring

import destructurableClasses.B

fun foo(f: (B) -> Unit) {
    val b = B()
    f(b)
}

fun main() {
    foo { (x, _, y, _) ->
        val task = object : Runnable {
            override fun run() {
                println()
            }
        }
        // STEP_INTO: 1
        // RESUME: 1
        //Breakpoint!
        task.run()

        val lambda = {
            println()
        }
        // STEP_INTO: 1
        // RESUME: 1
        //Breakpoint!
        lambda()

        val anonymousFun = fun() {
            println()
        }
        // STEP_INTO: 1
        // RESUME: 1
        //Breakpoint!
        anonymousFun()

        fun localFun() {
            println()
        }
        // STEP_INTO: 1
        //Breakpoint!
        localFun()
    }
}
