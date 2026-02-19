
fun String.foo(i: I ) {
    val l<caret>ower = lowercase()
    i.consume(this)
}

// IGNORE_K1