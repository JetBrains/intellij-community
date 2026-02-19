fun <T> anotherMethod(a: T, block: () -> Unit) {}

fun <T> aMethod(a: T & Any, block: () -> Unit) {
    anotherMethod(a, block)
}

fun main() {
    a<caret>Method(1) {}
}