// "Change to 'var'" "false"
// ACTION: Converts the assignment statement to an expression
// ACTION: Remove redundant assignment
// ERROR: Val cannot be reassigned
// K2_AFTER_ERROR: 'val' cannot be reassigned.
fun fun1(i: Int) {
    <caret>i = 2
}
