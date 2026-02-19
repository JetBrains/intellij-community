public inline fun <T, R> with(receiver: T, f: T.() -> R): R = receiver.f()

class A(val a: Int) {
    fun A.foo(p2: Int): Int {
        return 42
    }

    fun test() {
        val a1 = A(1)
        a1.foo(a1.a)
    }
}

fun test() {
    val t = with(A(1)) {
        val a1 = A(2)
        a1.foo(a1.a)
    }
}