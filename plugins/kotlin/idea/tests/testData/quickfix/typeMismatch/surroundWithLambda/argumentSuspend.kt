// "Surround with lambda" "true"
fun foo(action: suspend () -> String) {}

fun usage() {
    foo("oraora"<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix