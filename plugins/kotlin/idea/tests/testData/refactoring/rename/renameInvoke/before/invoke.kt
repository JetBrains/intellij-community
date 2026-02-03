class A(val n: Int) {
    operator fun invoke(i: Int): A = A(i)
}

fun test() {
    A(1).invoke(2)
    A(1)(2)
    val a = A(1)
    a(42).toString()
}

class Boo(val a: A)
class Bazz(val boo: Boo)
fun test(boo: Boo, baz: Bazz) {
    boo.a(2)
    baz.boo.a(2)
}