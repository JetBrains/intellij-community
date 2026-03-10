// "Surround with lambda" "true"
// PRIORITY: HIGH
// K2_ERROR: Assignment type mismatch: actual type is 'Int', but '() -> Long' was expected.
fun foo() {
    val block: () -> Long
    block = <caret>123
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix