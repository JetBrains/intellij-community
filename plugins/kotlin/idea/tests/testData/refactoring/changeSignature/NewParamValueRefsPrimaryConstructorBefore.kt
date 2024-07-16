open class A<caret>(val a: Int) {
    constructor(): this(1)
}

class B: A(1) {

}

class C: A {
    constructor(n: Int): super(n + 1)
}

fun test() = A(1)