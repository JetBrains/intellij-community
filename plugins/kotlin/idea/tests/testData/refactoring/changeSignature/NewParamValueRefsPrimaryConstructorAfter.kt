open class A(val a: Int, p2: Int) {
    constructor(): this(1, 1 + 1)
}

class B: A(1, 1 + 1) {

}

class C: A {
    constructor(n: Int): super(n + 1, n + 1 + 1)
}

fun test() = A(1, 1 + 1)
