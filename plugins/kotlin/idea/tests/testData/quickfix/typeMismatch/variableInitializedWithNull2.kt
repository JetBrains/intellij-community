// "Change type of 'x' to 'String?'" "false"
// ACTION: Convert assignment to assignment expression
// ACTION: Remove braces from 'if' statement
// ACTION: To raw string literal
// ERROR: Type mismatch: inferred type is String but Nothing? was expected
// ERROR: Val cannot be reassigned
fun foo(condition: Boolean) {
    val x = null
    if (condition) {
        x = "abc"<caret>
    }
}