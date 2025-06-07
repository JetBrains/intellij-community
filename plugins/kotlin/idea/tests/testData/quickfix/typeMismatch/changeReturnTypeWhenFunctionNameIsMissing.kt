// "Remove explicitly specified return type of enclosing function" "true"
// ERROR: Function declaration must have a name
// K2_AFTER_ERROR: Function declaration must have a name.
fun (): Int {
    return<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix