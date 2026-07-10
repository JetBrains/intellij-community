// "Convert string to character literal" "false"
// ACTION: Convert to 'buildString' call
// ACTION: Convert to raw string literal
// ACTION: Enable a trailing comma by default in the formatter
// ERROR: Incompatible types: String and Char
// K2_AFTER_ERROR: INCOMPATIBLE_TYPES
// K2_ERROR: INCOMPATIBLE_TYPES
fun test(c: Char) {
    when (c) {
        <caret>".." -> {}
    }
}
