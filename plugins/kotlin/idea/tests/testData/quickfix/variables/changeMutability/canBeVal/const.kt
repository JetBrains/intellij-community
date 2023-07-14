// "Change to 'val'" "true"
object Test {
    <caret>const var foo = "123"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix