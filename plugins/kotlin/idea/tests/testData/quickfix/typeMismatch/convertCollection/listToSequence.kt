// "Convert expression to 'Sequence' by inserting '.asSequence()'" "true"
// WITH_STDLIB

fun foo(a: List<String>) {
    bar(a<caret>)
}

fun bar(a: Sequence<String>) {}