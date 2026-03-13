// "Change to 'var'" "true"
// K2_ERROR: 'val' cannot be reassigned.
class A(val a: Int) {
    fun foo() {
        <caret>a = 5
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
