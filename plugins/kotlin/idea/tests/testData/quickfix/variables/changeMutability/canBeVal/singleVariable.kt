// "Change to 'val'" "true"
fun foo(p: Int) {
    <caret>var v: Int
    if (p > 0) v = 1 else v = 2
}
/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix