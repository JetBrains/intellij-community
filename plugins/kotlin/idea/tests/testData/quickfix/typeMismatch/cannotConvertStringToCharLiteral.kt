// "Convert string to character literal" "false"
// ERROR: Type mismatch: inferred type is String but Int was expected
fun bar(): Int {
    return <caret>"a"
}