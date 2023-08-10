// "Convert expression to 'Array' by inserting '.toList().toTypedArray()'" "true"
// WITH_STDLIB

fun foo(a: Iterable<String>) {
    bar(a<caret>)
}

fun bar(a: Array<String>) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix