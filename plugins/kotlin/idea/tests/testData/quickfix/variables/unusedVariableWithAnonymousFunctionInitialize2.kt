// "Remove variable 'a'" "true"
fun test() {
    val <caret>a: (String) -> Unit = fun(s: String) { s + s }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix$RemoveVariableFactory$doCreateQuickFix$removePropertyFix$1