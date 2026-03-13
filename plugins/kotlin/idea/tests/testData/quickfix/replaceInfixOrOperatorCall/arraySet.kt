// "Replace with safe (?.) call" "true"
// WITH_STDLIB
// K2_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'Array<String>?'.

fun foo(array: Array<String>?) {
    array<caret>[0] = ""
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix