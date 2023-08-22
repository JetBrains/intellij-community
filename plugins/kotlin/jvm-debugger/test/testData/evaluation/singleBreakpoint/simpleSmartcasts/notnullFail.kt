// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package notnullFail

fun main(args: Array<String>) {
    test(null)
}

fun test(nullable: String?) =
    //Breakpoint!
    nullable != null && nullable.length == 2

// EXPRESSION: nullable.length
// RESULT: java.lang.NullPointerException
