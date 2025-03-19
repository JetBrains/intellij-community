// "Change type of 'x' to 'String?'" "false"
// ACTION: Convert to 'buildString' call
// ACTION: Convert to raw string literal
// ACTION: Converts the assignment statement to an expression
// ACTION: Remove braces from 'if' statement
// ERROR: Type mismatch: inferred type is String but Int? was expected
// K2_AFTER_ERROR: Assignment type mismatch: actual type is 'String', but 'Int?' was expected.
fun foo(condition: Boolean) {
    var x: Int? = null
    if (condition) {
        x = "abc"<caret>
    }
}
