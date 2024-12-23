// "Change parameter 'x' type of function 'foo' to 'T'" "true"
// LANGUAGE_VERSION: 1.8

fun <T> foo(x: T & Any) {}

fun <T> bar(x: T) {
    foo((<caret>x))
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix

// Same example as in testData/quickfix/typeMismatch/parameterTypeMismatch/changeFunctionParameterTypeParenthesis.kt
// but shows another quick fix