// "Change to 'var'" "true"
// K2_ERROR: VAR_OVERRIDDEN_BY_VAL
open class A {
    open var x = 42;
}

class B(override val<caret> x: Int) : A()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix