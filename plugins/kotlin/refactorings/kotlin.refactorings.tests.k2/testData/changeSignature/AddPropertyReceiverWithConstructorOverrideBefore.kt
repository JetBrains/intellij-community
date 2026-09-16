open class A {
    open var <caret>p: Int = 1
}

class B(override var p: Int) : A()

fun test() {

    with(B(42)) {
        val t = p
        p = 3
    }
}