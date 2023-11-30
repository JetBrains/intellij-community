// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

fun main() {
    //Breakpoint!
    listOf("a")
}

// Force a step into the standard library listOf method.
//
// STEP_INTO_IGNORE_FILTERS: 1
//
// At the breakpoint in the standard library, use a stdlib inline function
// in an expression evaluation.
//
// EXPRESSION: listOf(1).map { it }
// RESULT: instance of java.util.ArrayList(id=ID): Ljava/util/ArrayList;

// IGNORE_K2