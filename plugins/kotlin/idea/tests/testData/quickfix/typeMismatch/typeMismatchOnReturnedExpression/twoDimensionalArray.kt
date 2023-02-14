// "Change type of 'b' to 'Array<Int>'" "false"
// ACTION: Add explicit type arguments
// ACTION: Add full qualifier
// ACTION: Change type of 'b' to 'Array<Array<Int>>'
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Introduce import alias
// DISABLE-ERRORS
// WITH_STDLIB
val a: Array<Int> = arrayOf(1)
val b: Array<Int> = <caret>arrayOf(a)
