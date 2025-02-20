// "Convert string to character literal" "false"
// ERROR: Type mismatch: inferred type is String but Int was expected
// K2_AFTER_ERROR: Return type mismatch: expected 'Int', actual 'String'.
fun bar(): Int {
    return <caret>"a"
}