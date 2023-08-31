// "Change to 'var'" "true"

val String.prop: Int
    get() {
        val p = 1
        <caret>p = 2
        return p
    }
/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix