open class A(open val b: Int)

class B : A("b") {
    override val b: Int
        get() = super.b
}

class C(override val b: Int) : A(b)

fun t() {
    val a = A("aa")
    a.b

    val b = B()
    b.b

    val c = C("c")
    c.b
}