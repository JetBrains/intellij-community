// "Surround with lambda" "true"
// PRIORITY: HIGH
// ERROR: Type mismatch: inferred type is String? but String was expected
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun nullableFn() {
    val nullableStr: String? = null
    str(<caret>nullableStr)
}

fun str(block: () -> String) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix