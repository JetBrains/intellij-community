// "Replace with 'emptySequence()' call" "true"
// WITH_STDLIB
// K2_ERROR: NULL_FOR_NONNULL_TYPE

fun foo(a: String?): Sequence<String> {
    val w = a ?: return null<caret>
    return sequenceOf(w)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix