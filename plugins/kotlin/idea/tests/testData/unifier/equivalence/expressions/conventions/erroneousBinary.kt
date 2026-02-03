// DISABLE_ERRORS
fun test(f: (Int) -> Unit) {
    f(<selection>1 +</selection>)
    f(1 +)
    f(+ 1)
    f(2 +)
}