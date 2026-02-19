// "Surround with lambda" "true"
// PRIORITY: HIGH
fun int() {
    i(<caret>123)
}

fun i(block: () -> Long) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix