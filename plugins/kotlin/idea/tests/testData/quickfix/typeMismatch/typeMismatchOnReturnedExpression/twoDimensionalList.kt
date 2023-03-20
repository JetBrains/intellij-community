// "Change type of 'b' to 'List<Int>'" "false"
// ACTION: Add explicit type arguments
// ACTION: Add full qualifier
// ACTION: Add names to call arguments
// ACTION: Change type of 'b' to 'List<List<Int>>'
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Introduce import alias
// DISABLE-ERRORS
// WITH_STDLIB
val a: List<Int> = listOf(1)
val b: List<Int> = <caret>listOf(a)
