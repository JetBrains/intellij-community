// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package localFunction

fun main(args: Array<String>) {
    fun inner() {
        //Breakpoint!
        val x = args
    }
    inner()
}

// EXPRESSION: args.size
// RESULT: 0: I