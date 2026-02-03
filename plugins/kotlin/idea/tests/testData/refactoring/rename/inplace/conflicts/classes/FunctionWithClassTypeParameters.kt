// NEW_NAME: A
// RENAME: member

class A<T: String>(t: T)
fun <caret>f(a: Int) {}


fun test() {
    f(1)
    val anAnyClass = A("")
}

