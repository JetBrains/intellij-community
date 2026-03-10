// "Surround with lambda" "true"
// PRIORITY: HIGH
// K2_ERROR: Argument type mismatch: actual type is 'String', but '() -> String' was expected.
fun simple() {
    str(<caret>"foo")
}

fun str(block: () -> String) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix