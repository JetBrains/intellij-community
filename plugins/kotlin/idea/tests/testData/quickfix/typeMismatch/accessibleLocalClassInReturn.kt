// "Change return type of enclosing function 'bar' to 'A'" "true"
// K2_ERROR: Return type mismatch: expected 'Int', actual '<anonymous>'.
fun foo() {
    open class A

    fun bar(): Int {
        return <caret>object: A() {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix