// "Change to 'var'" "true"
val a = 4

fun bar() {
    <caret>a = 5
}

/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix