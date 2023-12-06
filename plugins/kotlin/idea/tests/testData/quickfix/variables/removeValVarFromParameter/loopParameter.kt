// "Remove 'val' from parameter" "true"
fun f(list: List<String>) {
    for (val<caret> x in list) {

    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveValVarFromParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveValVarFromParameterFix