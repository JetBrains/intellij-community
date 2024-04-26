// NEW_NAME: f
// RENAME: member

class <caret>A<T: String>(t: T)
fun f(a: Int) {}


fun test() {
    f(1)
    val anAnyClass = A("")
}

