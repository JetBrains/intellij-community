open class A(open var b: Int)

class B : A("b") {
    override var b: Int
        get() = super.b
        set(value) = Unit
}

class C(override var b: Int) : A(b)

fun t() {
    val a = A("aa")
    a.b
    a.b = ""

    val b = B()
    b.b
    b.b = ""

    val c = C("c")
    c.b
    c.b = ""
}