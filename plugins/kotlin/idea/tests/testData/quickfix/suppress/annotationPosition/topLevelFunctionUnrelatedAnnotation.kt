// "Suppress 'DIVISION_BY_ZERO' for fun foo" "true"

@ann fun foo() = 2 / <caret>0

annotation class ann

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction