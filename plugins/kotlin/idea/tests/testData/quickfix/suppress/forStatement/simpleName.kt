// "Suppress 'UNUSED_EXPRESSION' for statement " "true"

fun foo() {
    val a = 1
    <caret>a
}

// IGNORE_K2
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction