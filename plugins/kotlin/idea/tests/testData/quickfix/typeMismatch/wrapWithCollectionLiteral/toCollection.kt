// "Wrap element with 'listOf()' call" "true"
// WITH_STDLIB

fun foo(a: String) {
    bar(a<caret>)
}

fun bar(a: Collection<String>) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix