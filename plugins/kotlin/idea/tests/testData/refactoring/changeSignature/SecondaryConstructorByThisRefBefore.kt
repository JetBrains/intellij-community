open class A {
    constructor(a: Int) {

    }

    constructor(): <caret>this(1) {

    }
}

open class B: A {
    constructor(a: Int): super(a) {

    }
}

fun test() {
    A(1)
}