// "Suppress 'UNNECESSARY_NOT_NULL_ASSERTION' for statement " "true"

fun foo() {
    1<caret>!! in (1..2)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction