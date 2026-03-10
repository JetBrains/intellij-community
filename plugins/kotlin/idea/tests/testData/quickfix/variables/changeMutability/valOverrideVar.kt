// "Change to 'var'" "true"
// K2_ERROR: 'var' property 'var x: Int' defined in 'A' cannot be overridden by 'val' property 'val x: Int'.
open class A {
    open var x = 42;
}

class B : A() {
    override val<caret> x: Int = 3;
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix