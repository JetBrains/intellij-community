// NEW_NAME: r
// RENAME: member
// SHOULD_FAIL_WITH: Function 'c' will be shadowed by function 'r'
class Receiver{
    fun r() {}
}
class Container {
    fun <caret>c() {}
}

context(Receiver)
fun Container.f() {
    r()
    c()
}

context(Container)
fun Receiver.f() {
    r()
    c()
}

// IGNORE_K1