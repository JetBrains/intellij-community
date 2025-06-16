package test

actual open class C {
    actual open fun m() { }
}

fun test() {
    C().m()
}