// "Change parameter 'x' type of function 'bar' to 'T & Any'" "true"
// LANGUAGE_VERSION: 1.8
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
package a

fun <T> foo(x: T & Any) {}

fun <T> bar(x: T) {
    foo(x<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix
// IGNORE_K2