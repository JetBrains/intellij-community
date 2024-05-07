class A(val n: Int) {
    fun foo(i: Int): A = A(i)
}

fun test() {
    A(1).foo(2)
    A(1).foo(2)
}

class Boo(val a: A)

fun test(boo: Boo) {
    boo.a.foo(2)
}