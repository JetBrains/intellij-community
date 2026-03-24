// "Change type of 'x' to 'Int'" "true"
// K2_ERROR: Initializer type mismatch: expected 'Short', actual 'Int'.

fun foo() {
    val x: Short = <caret>100000
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix