class A(val n: Int) {
    operator fun get(i: Int): A = A(i)
}

fun test() {
    A(1).get(2)
    A(1)[2]
}

class Boo(val a: A)

fun test(boo: Boo) {
    boo.a[2]
}