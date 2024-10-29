// "Surround with lambda" "true"
fun foo() {
    val block: () -> String = <caret>"foo"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix