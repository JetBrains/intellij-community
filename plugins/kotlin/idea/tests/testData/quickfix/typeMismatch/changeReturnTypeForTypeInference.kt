// "Specify 'List<Any>' return type for enclosing function 'test2'" "true"
// WITH_STDLIB
// K2_ERROR: RETURN_TYPE_MISMATCH

fun test2(ss: List<Any>) {
    return ss.map { it }<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix