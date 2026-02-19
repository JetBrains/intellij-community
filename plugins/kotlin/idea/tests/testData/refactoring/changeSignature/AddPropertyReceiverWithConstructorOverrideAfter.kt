open class A {
    open var String.p: Int = 1
}

class B(override var String.p: Int) : A()

fun test() {

    with(B(42)) {
        val t = "".p
        "".p = 3
    }
}
