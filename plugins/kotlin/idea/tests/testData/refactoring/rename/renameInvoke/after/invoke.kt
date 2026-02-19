class A(val n: Int) {
    fun foo(i: Int): A = A(i)
}

fun test() {
    A(1).foo(2)
    A(1).foo(2)
    val a = A(1)
    a.foo(42).toString()
}

class Boo(val a: A)
class Bazz(val boo: Boo)
fun test(boo: Boo, baz: Bazz) {
    boo.a.foo(2)
    baz.boo.a.foo(2)
}