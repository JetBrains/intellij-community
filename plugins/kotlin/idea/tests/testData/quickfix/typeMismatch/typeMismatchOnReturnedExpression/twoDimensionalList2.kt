// "Change type of 'b' to 'List<List<Int>>'" "true"
// WITH_STDLIB
val a: List<Int> = listOf(1)
val b: List<Int> = <caret>listOf(a)

/* IGNORE_FIR */