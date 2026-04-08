// "Suppress 'USELESS_CAST' for statement " "true"

fun foo(index: Int) {
    val arr = IntArray(1)
    arr[index a<caret>s Int]++
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction