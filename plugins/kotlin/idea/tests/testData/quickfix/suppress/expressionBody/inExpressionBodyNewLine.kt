// "Suppress 'DEPRECATION' for statement " "true"

@Deprecated("x")
class XXX

fun xxx() =
    XX<caret>X()

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction