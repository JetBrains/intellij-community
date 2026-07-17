// "Change to 'var'" "true"
// K2_ERROR: VAL_WITH_SETTER
class A() {
    val a: Int = 0
        <caret>set(v: Int) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix