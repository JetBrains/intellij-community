package test

import dependency.*

fun <Int> f(l: List<Int>) {
    val (e<caret>l1, el2, el3) = l
}

// ALLOW_AST_ACCESS

// REF: el1