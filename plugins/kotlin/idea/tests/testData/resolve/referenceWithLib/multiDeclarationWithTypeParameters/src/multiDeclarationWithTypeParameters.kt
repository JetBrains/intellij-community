package test

import dependency.*

fun <Int> f(l: List<Int>) {
    val (e<caret>l1, el2, el3) = l
}

// REF: el1
// SKIP_IS_REFERENCE_TO_CHECK