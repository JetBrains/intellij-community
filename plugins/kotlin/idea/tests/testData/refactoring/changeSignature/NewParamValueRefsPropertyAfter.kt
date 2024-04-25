class A(val n: Int) {
    fun foo(p2: Int): Int {
        return i
    }
}

fun test() {
    val a = A(1)
    a.foo(a.n + 1)
    with(A(1)) {
        foo(n + 1)
    }
}