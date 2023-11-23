// NEW_NAME: f
// RENAME: member
fun f(i: Int) {}
class A {
    fun <caret>b(i: Int) {}
}

fun test(a: A) {
    f(42)
    a.b(0)
}

