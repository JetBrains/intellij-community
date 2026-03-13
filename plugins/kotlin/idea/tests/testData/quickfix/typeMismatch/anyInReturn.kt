// "Specify 'Any' return type for enclosing function 'foo'" "true"
// K2_ERROR: Return type mismatch: expected 'Unit', actual 'A'.
fun foo() {
    class A

    return <caret>A()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix