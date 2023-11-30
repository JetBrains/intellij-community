// "Suppress 'DIVISION_BY_ZERO' for parameter p" "true"

fun foo(p: Int = 2 / <caret>0) = null

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction