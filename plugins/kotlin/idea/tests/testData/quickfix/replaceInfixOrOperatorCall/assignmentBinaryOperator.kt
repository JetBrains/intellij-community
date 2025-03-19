// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo(bar: Int?) {
    var i: Int = 1
    i = bar +<caret> 1
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix