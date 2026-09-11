// "Change to 'val'" "true"
// K2_ERROR: WRONG_MODIFIER_TARGET
object Test {
    <caret>const var foo = "123"
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix