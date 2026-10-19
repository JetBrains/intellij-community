fun foo(first: Array<String>) {
    bar(*first)
    bar(*first, *first)
}

fun bar(vararg elements: String) {}

fun test(values: Array<String>) {
    <caret>foo(first = values)
}
