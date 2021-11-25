// "Convert expression to 'Array' by inserting '.toTypedArray()'" "true"
// WITH_STDLIB

fun foo(a: List<String>, b: List<String>) {
    bar(a<caret> + b)
}

fun bar(a: Array<String>) {}