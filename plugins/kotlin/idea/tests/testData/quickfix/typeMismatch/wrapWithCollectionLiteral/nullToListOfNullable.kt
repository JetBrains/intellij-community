// "Wrap element with 'listOf()' call" "true"
// WITH_STDLIB

fun foo() {
    bar(null<caret>)
}

fun bar(a: List<String?>) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix