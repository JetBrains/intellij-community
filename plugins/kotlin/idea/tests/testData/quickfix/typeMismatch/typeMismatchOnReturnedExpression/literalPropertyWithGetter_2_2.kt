// "Change type of 'complex' to '(Int) -> Long'" "true"
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Return type mismatch: expected '(Int) -> String', actual '(Int) -> Long'.

val complex: (Int) -> String
    get() = { it.toLong()<caret> }

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix