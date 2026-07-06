// "Change type of 'z' to 'T & Any'" "true"
// ERROR: Type mismatch: inferred type is T but T & Any was expected
// K2_AFTER_ERROR: Initializer type mismatch: expected 'T (of fun <T> bar) & Any', actual 'T (of fun <T> bar)'.
// LANGUAGE_VERSION: 1.8
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
package a

fun <T> foo(x: T & Any) {}

fun <T> bar(x: T) {
    val z: T = x
    foo(z<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix
// IGNORE_K2