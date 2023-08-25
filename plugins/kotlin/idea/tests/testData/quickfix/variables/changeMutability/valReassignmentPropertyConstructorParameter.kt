// "Change to 'var'" "true"
class A(val a: Int) {
    fun foo() {
        <caret>a = 5
    }
}

/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix