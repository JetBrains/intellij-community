// "Change to '1'" "true"
// K2_ERROR: RETURN_TYPE_MISMATCH
fun test(): Int {
    return 1.0F<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrongPrimitiveLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongPrimitiveLiteralFix