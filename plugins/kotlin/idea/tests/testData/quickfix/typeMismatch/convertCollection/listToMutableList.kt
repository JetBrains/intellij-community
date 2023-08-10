// "Convert expression to 'MutableList' by inserting '.toMutableList()'" "true"
// WITH_STDLIB

fun foo(a: List<String>) {
    bar(a<caret>)
}

fun bar(a: MutableList<String>) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix