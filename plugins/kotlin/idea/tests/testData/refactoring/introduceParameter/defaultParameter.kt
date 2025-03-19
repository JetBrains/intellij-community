// WITH_DEFAULT_VALUE: false

fun p2() {
    p()
}
fun p(i: Int = get42()) {
    println(<selection>i + 1</selection>)
}

fun get42(): Int = 42

// IGNORE_K1