// "Surround with lambda" "true"
fun simple() {
    str(<caret>"foo")
}

fun str(block: () -> String) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix
/* IGNORE_K2 */