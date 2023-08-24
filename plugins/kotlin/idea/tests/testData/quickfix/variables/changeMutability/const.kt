// "Change to 'var'" "true"
object A {
    const val A = 1

    fun foo() {
        <caret>A = 10
    }
}
/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix