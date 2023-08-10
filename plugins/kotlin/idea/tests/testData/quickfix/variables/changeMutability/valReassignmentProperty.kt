// "Change to 'var'" "true"
class A() {
    val a = 1

    fun foo() {
        <caret>a = 5
    }
}

/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix