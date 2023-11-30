// "Suppress 'DIVISION_BY_ZERO' for fun foo" "true"
// ERROR: The integer literal does not conform to the expected type String

@Suppress(1)
fun foo() = 2 / <caret>0

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction