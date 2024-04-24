package test

actual open class C {
    actual fun m(b: Boolean) { }
}

fun test() {
    C().m(false)
}