// "Change type of 'x' to 'String?'" "false"
// ACTION: Converts the assignment statement to an expression
// ACTION: Convert to 'buildString' call
// ACTION: Remove braces from 'if' statement
// ACTION: To raw string literal
// ERROR: Type mismatch: inferred type is String but Int? was expected
fun foo(condition: Boolean) {
    var x: Int? = null
    if (condition) {
        x = "abc"<caret>
    }
}