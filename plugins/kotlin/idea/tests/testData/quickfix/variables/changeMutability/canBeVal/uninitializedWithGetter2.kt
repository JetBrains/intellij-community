// "Change to 'val'" "true"
class Test {
    var foo: Int<caret>
        get() {
            return 1
        }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix