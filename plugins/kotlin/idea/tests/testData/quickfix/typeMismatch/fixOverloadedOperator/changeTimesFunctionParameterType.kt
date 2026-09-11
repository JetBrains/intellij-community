// "Change parameter 'a' type of function 'times' to 'String'" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
interface A {
    operator fun times(a: A): A
}

fun foo(a: A): A = a * <caret>""
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix