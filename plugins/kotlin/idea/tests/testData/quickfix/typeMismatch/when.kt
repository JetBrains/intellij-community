// "Specify 'String' return type for enclosing function 'test'" "true"
// K2_ERROR: RETURN_TYPE_MISMATCH
fun test(i: Int) {
    return when (i) {
        0 -> ""<caret>
        else -> ""
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix