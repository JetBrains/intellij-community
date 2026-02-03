// "Suppress 'DIVISION_BY_ZERO' for fun foo" "true"
// ERROR: The integer literal does not conform to the expected type String
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'Int', but 'String' was expected.

@Suppress(1)
fun foo() = 2 / <caret>0

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction