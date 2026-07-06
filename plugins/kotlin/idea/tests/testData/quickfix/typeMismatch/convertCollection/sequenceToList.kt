// "Convert expression to 'List' by inserting '.toList()'" "true"
// WITH_STDLIB
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun foo(a: Sequence<String>) {
    bar(a<caret>)
}

fun bar(a: List<String>) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix