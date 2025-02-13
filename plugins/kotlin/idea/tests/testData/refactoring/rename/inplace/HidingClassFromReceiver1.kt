// NEW_NAME: R
// RENAME: member
// SHOULD_FAIL_WITH: Class 'C' will be shadowed by class 'R'
class Receiver{
    inner class R {}
}
class Container {
    inner class <caret>C {}
}

context(Receiver)
fun Container.f() {
    val r: Receiver.R = R()
    val c: Container.C = C()
}

context(Container)
fun Receiver.f() {
    val r: Receiver.R = R()
    val c: Container.C = C()
}
// IGNORE_K1