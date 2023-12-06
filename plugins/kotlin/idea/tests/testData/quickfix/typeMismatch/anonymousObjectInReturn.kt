// "Change return type of enclosing function 'foo' to 'T'" "true"
interface T

fun foo() {
    return <caret>object: T{}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix