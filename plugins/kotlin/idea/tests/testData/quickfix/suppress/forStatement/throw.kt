// "Suppress 'UNNECESSARY_NOT_NULL_ASSERTION' for statement " "true"
// WITH_STDLIB

fun foo(): Any {
    throw Exception(""<caret>!!)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction