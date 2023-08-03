open class A<caret>(open val a: String)

class B : A("b") {
    override val a: String
        get() = super.a
}

class C(override val a: String) : A(a)

fun t() {
    val a = A("aa")
    a.a

    val b = B()
    b.a

    val c = C("c")
    c.a
}