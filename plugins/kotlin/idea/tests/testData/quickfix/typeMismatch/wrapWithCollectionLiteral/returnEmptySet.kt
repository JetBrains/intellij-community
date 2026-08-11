// "Replace with 'emptySet()' call" "true"
// WITH_STDLIB
// K2_ERROR: NULL_FOR_NONNULL_TYPE

fun foo(a: String?): Set<String> {
    val w = a ?: return null<caret>
    return setOf(w)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix