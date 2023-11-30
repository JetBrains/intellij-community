open class A {
    open fun test(s1: String, s2: String, i: Int) = Unit
}

open class B : A() {
    override fun test(s1: String, s2: String, i: Int) = Unit
}

open class C : B() {
    override fun test(s1: String, s2: String, i: Int) = Unit
}

fun usages() {
    val a = A()
    a.test("s1", "s2", 42)
    a.test(s1 = "s1", s2 = "s2", i = 42)
    a.test("s1", s2 = "s2", 42)
    a.test(s1 = "s1", s2 = "s2", i = 42)

    val b = B()
    b.test("s1", "s2", 42)
    b.test(s1 = "s1", s2 = "s2", i = 42)
    b.test("s1", s2 = "s2", 42)
    b.test(s1 = "s1", s2 = "s2", i = 42)

    val c = C()
    c.test("s1", "s2", 42)
    c.test(s1 = "s1", s2 = "s2", i = 42)
    c.test("s1", s2 = "s2", 42)
    c.test(s1 = "s1", s2 = "s2", i = 42)
}