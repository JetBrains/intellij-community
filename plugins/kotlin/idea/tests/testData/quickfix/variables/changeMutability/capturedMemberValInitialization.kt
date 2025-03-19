// "Change to 'var'" "true"

class Test {
    val a: String

    init {
        val t = object {
            fun some() {
                <caret>a = "12"
            }
        }

        a = "2"
        t.some()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
