// "Suppress 'DIVISION_BY_ZERO' for fun local" "true"

fun foo() {
    fun local() = 2 / <caret>0
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction