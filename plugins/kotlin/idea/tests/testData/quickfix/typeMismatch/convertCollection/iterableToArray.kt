// "Convert expression to 'Array' by inserting '.toList().toTypedArray()'" "true"
// WITH_STDLIB

fun foo(a: Iterable<String>) {
    bar(a<caret>)
}

fun bar(a: Array<String>) {}