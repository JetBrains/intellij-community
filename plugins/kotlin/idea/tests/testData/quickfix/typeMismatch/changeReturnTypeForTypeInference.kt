// "Change return type of enclosing function 'test2' to 'List<Any>'" "true"
// WITH_STDLIB

fun test2(ss: List<Any>) {
    return ss.map { it }<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix