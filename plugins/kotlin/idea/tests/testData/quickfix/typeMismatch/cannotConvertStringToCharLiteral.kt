// "Convert string to character literal" "false"
// ERROR: Type mismatch: inferred type is String but Int was expected
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: RETURN_TYPE_MISMATCH
fun bar(): Int {
    return <caret>"a"
}