// "Surround with lambda" "true"
// PRIORITY: HIGH
// ERROR: Type mismatch: inferred type is String but (Int) -> String was expected
fun simple() {
    str(<caret>"foo")
}

fun str(block: (num: Int) -> String) {}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix