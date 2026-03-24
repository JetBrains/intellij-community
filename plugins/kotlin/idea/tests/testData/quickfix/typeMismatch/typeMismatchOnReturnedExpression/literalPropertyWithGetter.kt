// "Change type of 'complex' to '(Int) -> Long'" "true"
// K2_ERROR: Return type mismatch: expected 'String', actual 'Long'.

val complex: (Int) -> String
    get() = { it.toLong()<caret> }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix