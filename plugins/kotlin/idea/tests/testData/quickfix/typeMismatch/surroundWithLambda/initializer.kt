// "Surround with lambda" "true"
// PRIORITY: HIGH
// K2_ERROR: Initializer type mismatch: expected '() -> String', actual 'String'.
fun foo() {
    val block: () -> String = <caret>"foo"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix