// "Remove variable 'flag'" "true"

fun foo() {
    val <caret>flag = true
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix$RemoveVariableFactory$doCreateQuickFix$removePropertyFix$1
// IGNORE_K2
// Task for K2: KTIJ-29591