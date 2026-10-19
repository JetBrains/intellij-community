fun foo(first: String): List<String> {
    return listOf(first, "second")
}

fun test(cond: Boolean): List<String> {
    return <caret>foo(
        first = when (cond) {
            true -> "true"
            false -> "false"
        }
    )
}
