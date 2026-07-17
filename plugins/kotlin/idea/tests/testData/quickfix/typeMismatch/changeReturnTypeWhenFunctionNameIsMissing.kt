// "Remove explicitly specified return type of enclosing function" "true"
// ERROR: Function declaration must have a name
// K2_AFTER_ERROR: FUNCTION_DECLARATION_WITH_NO_NAME
// K2_ERROR: FUNCTION_DECLARATION_WITH_NO_NAME
// K2_ERROR: RETURN_TYPE_MISMATCH
fun (): Int {
    return<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix