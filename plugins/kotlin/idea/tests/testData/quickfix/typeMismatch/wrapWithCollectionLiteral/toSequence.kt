// "Wrap element with 'sequenceOf()' call" "true"
// WITH_STDLIB

fun foo(a: String) {
    bar(a<caret>)
}

fun bar(a: Sequence<String>) {}
