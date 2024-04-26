open class A {
    open fun te<caret>st(s1: String, s2: String) = Unit
}

open class B : A() {
    override fun test(s1: String, s2: String) = Unit
}

open class C : B() {
    override fun test(s1: String, s2: String) = Unit
}

fun usages() {
    val a = A()
    a.test("s1", "s2")
    a.test(s1 = "s1", s2 = "s2")
    a.test("s1", s2 = "s2")
    a.test(s1 = "s1", s2 = "s2")

    val b = B()
    b.test("s1", "s2")
    b.test(s1 = "s1", s2 = "s2")
    b.test("s1", s2 = "s2")
    b.test(s1 = "s1", s2 = "s2")

    val c = C()
    c.test("s1", "s2")
    c.test(s1 = "s1", s2 = "s2")
    c.test("s1", s2 = "s2")
    c.test(s1 = "s1", s2 = "s2")
}