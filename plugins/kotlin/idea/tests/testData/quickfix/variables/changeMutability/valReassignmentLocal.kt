// "Change to 'var'" "true"
// K2_ERROR: 'val' cannot be reassigned.
fun foo() {
    val a = 1
    <caret>a = 3
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
