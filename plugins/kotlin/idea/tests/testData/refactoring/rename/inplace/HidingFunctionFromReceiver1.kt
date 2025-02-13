// NEW_NAME: c
// RENAME: member
// SHOULD_FAIL_WITH: Function 'r' will be shadowed by function 'c'
class Receiver{
    fun <caret>r() {}
}
class Container {
    fun c() {}
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