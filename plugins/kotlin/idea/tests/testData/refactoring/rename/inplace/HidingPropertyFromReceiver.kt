// NEW_NAME: r
// RENAME: member
// SHOULD_FAIL_WITH: Parameter 'c' will be shadowed by parameter 'r'
class Receiver(val r: String)
class Container(val <caret>c: String) {
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