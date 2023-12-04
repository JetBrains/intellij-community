operator fun Any.inv<caret>oke(i: Int, action: () -> Unit) {}

fun foo(): Int = 42

fun test() {
    foo()(42){ }
}