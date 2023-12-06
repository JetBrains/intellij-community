// NEW_NAME: c
// RENAME: member
class Receiver(val <caret>r: String)
class Container(val c: String) {
    fun Receiver.respond() {
        println(r)
        println(c)
    }
}

context(Receiver)
fun Container.f() {
    println(r)
    println(c)
}

context(Container)
fun Receiver.f() {
    println(r)
    println(c)
}

// IGNORE_K1