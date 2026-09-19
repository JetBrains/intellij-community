// "Remove variable 'i'" "true"

fun foo() {
    val <caret>i: Int? = null
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveUnusedVariableFix