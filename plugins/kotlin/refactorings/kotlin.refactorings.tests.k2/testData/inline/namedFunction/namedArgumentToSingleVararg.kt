fun foo(first: String) {
    bar(first)
}

fun bar(vararg elements: String) {}

fun test() {
    <caret>foo(first = "a")
}
