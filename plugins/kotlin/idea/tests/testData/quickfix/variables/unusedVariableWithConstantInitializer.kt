// "Remove variable 'flag'" "true"

fun foo() {
    val <caret>flag = true
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveUnusedVariableFix