// WITH_DEFAULT_VALUE: false
fun f(g: () -> Unit) {
    println(<selection>123</selection>)
}

fun call() {
    f {}
}