// "Change to 'var'" "true"
fun foo() {
    val a = 1
    <caret>a = 3
}

/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix