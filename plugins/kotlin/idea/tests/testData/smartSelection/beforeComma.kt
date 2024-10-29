// WITH_STDLIB
fun test(foo: String) {
    listOf(foo<caret>, "again")
}

/*
foo
listOf(…)
*/