// "Replace with 'emptyList()' call" "true"
// WITH_STDLIB
// K2_ERROR: Null cannot be a value of a non-null type 'List<String>'.

fun foo(a: String?): List<String> {
    val w = a ?: return null<caret>
    return listOf(w)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix