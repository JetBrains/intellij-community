// "Change type of 'z' to 'T & Any'" "true"
// LANGUAGE_VERSION: 1.8
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
package a

fun <T> foo(x: T & Any) {}

fun <T> bar(x: T & Any) {
    val z: T = x
    foo(z<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix
// IGNORE_K2