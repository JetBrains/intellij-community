// "Change type of 'z' to 'T & Any'" "true"
// LANGUAGE_VERSION: 1.8
package a

fun <T> foo(x: T & Any) {}

fun <T> bar(x: T & Any) {
    val z: T = x
    foo(z<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix
// IGNORE_K2