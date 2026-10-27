// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package enumSynthetic

interface I

enum class E : I { A, B }

fun main(args: Array<String>) {
    val e: I = E.A
    //Breakpoint!
    println(e)
}

// EXPRESSION: e.entries
// RESULT: instance of kotlin.enums.EnumEntriesList(id=ID): Lkotlin/enums/EnumEntriesList;

// EXPRESSION: e.values()
// RESULT: instance of enumSynthetic.E[2] (id=ID): [LenumSynthetic/E;
