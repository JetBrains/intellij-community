// "Wrap element with 'listOf()' call" "true"
// WITH_STDLIB

fun foo() {
    bar(null<caret>)
}

fun bar(a: List<String?>) {}
