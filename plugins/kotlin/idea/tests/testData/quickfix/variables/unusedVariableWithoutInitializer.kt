// "Remove variable 'test' (may change semantics)" "true"
fun f() {
    val <caret>test: Int
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveUnusedVariableFix