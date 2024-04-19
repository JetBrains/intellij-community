package test

actual open class C {
    actual fun m() { }
}

fun test() {
    C().m()
}