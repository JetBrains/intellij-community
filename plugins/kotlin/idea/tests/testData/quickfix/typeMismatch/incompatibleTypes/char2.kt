// "Convert string to character literal" "false"
// ACTION: Convert to 'buildString' call
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: To raw string literal
// ERROR: Incompatible types: String and Char
fun test(c: Char) {
    when (c) {
        <caret>".." -> {}
    }
}
