// NEW_NAME: C
// RENAME: member
class Receiver{
    inner class <caret>R {}
}
class Container {
    inner class C {}
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