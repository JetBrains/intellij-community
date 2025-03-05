// "Surround with lambda" "true"
// PRIORITY: HIGH
fun simple() {
    str(<caret>"foo")
}

fun str(block: () -> String) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix