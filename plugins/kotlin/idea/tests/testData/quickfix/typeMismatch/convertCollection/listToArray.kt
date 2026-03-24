// "Convert expression to 'Array' by inserting '.toTypedArray()'" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'List<String>', but 'Array<String>' was expected.

fun foo(a: List<String>) {
    bar(a<caret>)
}

fun bar(a: Array<String>) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix