// "Change to 'var'" "true"
// K2_ERROR: 'val' cannot be reassigned.
val a = 4

fun bar() {
    <caret>a = 5
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
