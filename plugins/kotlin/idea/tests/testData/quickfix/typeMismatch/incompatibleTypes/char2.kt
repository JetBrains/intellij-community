// "Convert string to character literal" "false"
// ACTION: Convert to 'buildString' call
// ACTION: Convert to raw string literal
// ACTION: Enable a trailing comma by default in the formatter
// ERROR: Incompatible types: String and Char
// K2_AFTER_ERROR: Incompatible types 'Char' and 'String'.
fun test(c: Char) {
    when (c) {
        <caret>".." -> {}
    }
}
