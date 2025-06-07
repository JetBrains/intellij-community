// "Suppress 'DIVISION_BY_ZERO' for fun foo" "true"
// ERROR: Unresolved reference: ann
// K2_AFTER_ERROR: Unresolved reference 'ann'.

@ann fun foo() = 2 / <caret>0

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction