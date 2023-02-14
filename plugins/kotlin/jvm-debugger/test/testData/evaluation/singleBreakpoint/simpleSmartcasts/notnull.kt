// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package notnull

fun main(args: Array<String>) {
    test("OK")
}

fun test(nullable: String?) =
    nullable != null &&
            //Breakpoint!
            nullable.length == 2

// EXPRESSION: nullable.length
// RESULT: 2: I
