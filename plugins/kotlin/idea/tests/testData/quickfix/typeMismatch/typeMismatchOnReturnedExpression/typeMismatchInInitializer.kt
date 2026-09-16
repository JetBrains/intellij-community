// "Change return type of enclosing function 'foo' to 'String'" "true"
// K2_ERROR: RETURN_TYPE_MISMATCH
fun foo(): Int = <caret>""
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix