public inline fun <T, R> with(receiver: T, f: T.() -> R): R = receiver.f()

class A(val a: Int) {
    fun foo(x: A, p2: Int): Int {
        return 0
    }
}

fun test() {
    val a = A(1)
    a.foo(A(2), a.a)
    with(A(1)) {
        foo(A(2), this.a)
        this.foo(A(2), this.a)
    }
}
