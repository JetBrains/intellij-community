// "Surround with lambda" "true"
// PRIORITY: HIGH
fun foo(): () -> String? {
    return <caret>"foo"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix