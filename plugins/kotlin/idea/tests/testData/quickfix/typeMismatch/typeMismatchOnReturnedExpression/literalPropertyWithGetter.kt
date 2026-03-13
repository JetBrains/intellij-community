// "Change type of 'complex' to '(Int) -> Long'" "true"
// K2_ERROR: Return type mismatch: expected 'String', actual 'Long'.

val complex: (Int) -> String
    get() = { it.toLong()<caret> }

// IGNORE_K2
// TODO: Drop IGNORE_K2 when KTIJ-36918 is fixed

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix