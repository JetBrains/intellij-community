// "Replace with 'emptySequence()' call" "true"
// WITH_STDLIB

fun foo(a: String?): Sequence<String> {
    val w = a ?: return null<caret>
    return sequenceOf(w)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix