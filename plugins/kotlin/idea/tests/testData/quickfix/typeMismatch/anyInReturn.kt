// "Change return type of enclosing function 'foo' to 'Any'" "true"
fun foo() {
    class A

    return <caret>A()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix