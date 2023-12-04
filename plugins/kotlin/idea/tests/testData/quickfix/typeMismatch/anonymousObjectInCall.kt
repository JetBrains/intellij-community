// "Change parameter 't' type of function 'foo' to 'T'" "true"
interface T

fun Int.foo(t: Int) {

}

fun foo() {
    1.foo(<caret>object: T{})
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix