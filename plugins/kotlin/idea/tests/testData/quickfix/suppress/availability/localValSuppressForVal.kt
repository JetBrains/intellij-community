// "Suppress 'DIVISION_BY_ZERO' for val a" "true"

fun foo() {
    val a = 2 / <caret>0
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction