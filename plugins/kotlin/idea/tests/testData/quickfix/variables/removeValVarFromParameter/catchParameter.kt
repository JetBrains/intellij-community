// "Remove 'val' from parameter" "true"
// WITH_STDLIB
fun f() {
    try {

    } catch (<caret>val e: Exception) {

    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveValVarFromParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveValVarFromParameterFix