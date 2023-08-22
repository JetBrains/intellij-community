// "Change return type of enclosing function 'boo' to 'String'" "true"
fun boo(): Int {
    return ((if (true) {
        val a = ""
        <caret>a
    } else ""))
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix