// NEW_NAME: a
// RENAME: member
class A {
   fun a() {}
}

fun <caret>m() {}
fun f() {
    with(A()) {
        a()
    }
}