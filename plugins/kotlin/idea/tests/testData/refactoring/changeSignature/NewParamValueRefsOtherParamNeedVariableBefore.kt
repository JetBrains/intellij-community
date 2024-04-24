public inline fun <T, R> with(receiver: T, f: T.() -> R): R = receiver.f()

class A(val a: Int) {
    fun f<caret>oo(x: A): Int {
        return 0
    }
}

fun test() {
    A(1).foo(A(2))
    with(A(1)) {
        foo(A(2))
        this.foo(A(2))
    }
}