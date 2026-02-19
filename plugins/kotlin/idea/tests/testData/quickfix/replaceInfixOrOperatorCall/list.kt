// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo(list: List<String>?) {
    list<caret>[0]
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix