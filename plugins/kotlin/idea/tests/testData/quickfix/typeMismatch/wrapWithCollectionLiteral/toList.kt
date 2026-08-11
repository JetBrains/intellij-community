// "Wrap element with 'listOf()' call" "true"
// WITH_STDLIB
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun foo(a: String) {
    bar(a<caret>)
}

fun bar(a: List<String>) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix