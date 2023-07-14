// "Suppress 'USELESS_CAST' for statement " "true"

fun foo() {
    val arr = IntArray(1)
    ++arr[1 a<caret>s Int]
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction