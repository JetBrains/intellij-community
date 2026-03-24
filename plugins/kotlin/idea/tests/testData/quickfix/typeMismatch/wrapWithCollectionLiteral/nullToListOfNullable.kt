// "Wrap element with 'listOf()' call" "true"
// WITH_STDLIB
// K2_ERROR: Null cannot be a value of a non-null type 'List<String?>'.

fun foo() {
    bar(null<caret>)
}

fun bar(a: List<String?>) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix