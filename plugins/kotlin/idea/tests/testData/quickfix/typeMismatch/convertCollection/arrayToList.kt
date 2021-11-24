// "Convert expression to 'List' by inserting '.toList()'" "true"
// WITH_STDLIB

fun foo(a: Array<String>) {
    bar(a<caret>)
}

fun bar(a: List<String>) {}