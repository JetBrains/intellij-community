// "Change type of 'x' to 'String?'" "false"
// ACTION: Convert to 'buildString' call
// ACTION: Converts the assignment statement to an expression
// ACTION: Remove braces from 'if' statement
// ACTION: To raw string literal
// ERROR: Type mismatch: inferred type is String but Int was expected
fun foo(condition: Boolean) {
    var x = 1
    if (condition) {
        x = "abc"<caret>
    }
}