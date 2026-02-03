// "Replace with safe (this?.) call" "true"
// WITH_STDLIB
fun String?.foo() {
    <caret>lowercase()
}
// IGNORE_K2
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceImplicitReceiverCallFix