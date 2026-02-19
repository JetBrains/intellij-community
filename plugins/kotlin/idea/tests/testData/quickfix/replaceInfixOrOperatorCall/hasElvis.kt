// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo(list: List<String>?) {
    var s = ""
    s = list[0]<caret> ?: ""
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix