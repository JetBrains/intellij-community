// "Surround with lambda" "true"
// PRIORITY: HIGH
// ERROR: Type mismatch: inferred type is String but (Int) -> String was expected
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun simple() {
    str(<caret>"foo")
}

fun str(block: (num: Int) -> String) {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix