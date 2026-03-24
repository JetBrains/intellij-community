// "Convert expression to 'List' by inserting '.toList()'" "true"
// WITH_STDLIB
// K2_ERROR: Initializer type mismatch: expected 'List<String>', actual 'Sequence<String>'.

fun foo(a: Sequence<String>) {
    val strings: List<String> = a<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix