// "Remove variable 'i'" "true"

fun foo() {
    val <caret>i: Int? = null
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix$RemoveVariableFactory$doCreateQuickFix$removePropertyFix$1
// IGNORE_K2
// Task for K2: KTIJ-29591