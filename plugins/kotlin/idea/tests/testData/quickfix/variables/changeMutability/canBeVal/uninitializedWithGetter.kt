// "Change to 'val'" "true"
// K2_ERROR: MUST_BE_INITIALIZED
class Test {
    var foo<caret>
        get() = 1
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix