// "Replace with safe (?.) call" "true"
// WITH_STDLIB
// K2_ERROR: UNSAFE_OPERATOR_CALL

fun foo(bar: String?) {
    "foo" in<caret> bar
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix