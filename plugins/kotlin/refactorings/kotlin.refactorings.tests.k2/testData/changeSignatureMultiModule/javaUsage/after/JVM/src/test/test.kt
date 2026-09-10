package test

actual open class C {
    actual open fun m(b: Boolean) { }
}

fun test() {
    C().m(false)
}