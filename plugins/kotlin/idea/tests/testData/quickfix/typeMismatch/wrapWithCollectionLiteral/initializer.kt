// "Wrap element with 'listOf()' call" "true"
// WITH_STDLIB
// K2_ERROR: INITIALIZER_TYPE_MISMATCH

fun foo(a: String) {
    val strings: List<String> = a<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix