// "Change return type of enclosing function 'foo' to 'Int'" "true"
// WITH_STDLIB
fun foo(n: Int): Boolean {
    n.let {
        return@foo <caret>1
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix