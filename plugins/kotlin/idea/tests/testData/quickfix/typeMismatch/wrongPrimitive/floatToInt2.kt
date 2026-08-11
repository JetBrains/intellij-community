// "Change to '1'" "true"
// K2_ERROR: INITIALIZER_TYPE_MISMATCH
val a : Int = 1F<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrongPrimitiveLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongPrimitiveLiteralFix