// "Change type of 'x' to 'String?'" "false"
// ACTION: Convert to 'buildString' call
// ACTION: Convert to raw string literal
// ACTION: Converts the assignment statement to an expression
// ACTION: Remove braces from 'if' statement
// ERROR: Type mismatch: inferred type is String but Nothing? was expected
// ERROR: Val cannot be reassigned
// K2_AFTER_ERROR: ASSIGNMENT_TYPE_MISMATCH
// K2_AFTER_ERROR: VAL_REASSIGNMENT
// K2_ERROR: ASSIGNMENT_TYPE_MISMATCH
// K2_ERROR: VAL_REASSIGNMENT
fun foo(condition: Boolean) {
    val x = null
    if (condition) {
        x = "abc"<caret>
    }
}
