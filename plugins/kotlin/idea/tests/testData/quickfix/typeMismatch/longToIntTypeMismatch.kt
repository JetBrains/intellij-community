// "Change type of 'x' to 'Long'" "true"
// K2_ERROR: Initializer type mismatch: expected 'Int', actual 'Long'.

fun foo() {
    val x: Int = <caret>0L
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix