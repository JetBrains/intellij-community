open class A<caret>(open var a: String)

class B : A("b") {
    override var a: String
        get() = super.a
        set(value) = Unit
}

class C(override var a: String) : A(a)

fun t() {
    val a = A("aa")
    a.a
    a.a = ""

    val b = B()
    b.a
    b.a = ""

    val c = C("c")
    c.a
    c.a = ""
}