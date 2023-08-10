// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo() {}

fun bar() {
    val fff: (() -> Unit)? = ::foo
    <caret>fff()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix