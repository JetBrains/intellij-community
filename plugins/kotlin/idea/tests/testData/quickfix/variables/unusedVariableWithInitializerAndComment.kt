// "Remove variable 'a' (may change semantics)" "true"

var cnt = 5
fun getCnt() = cnt++
fun f() {
    var <caret>a = getCnt() // comment
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveUnusedVariableFix