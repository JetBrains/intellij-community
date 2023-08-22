// "Suppress 'DIVISION_BY_ZERO' for statement " "true"

fun foo() {
    call(2 / <caret>0)
}

fun call(i: Int) {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction