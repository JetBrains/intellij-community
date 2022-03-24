// "Wrap element with 'setOf()' call" "true"
// WITH_STDLIB

fun foo(a: String) {
    bar(a<caret>)
}

fun bar(a: Set<String>) {}
