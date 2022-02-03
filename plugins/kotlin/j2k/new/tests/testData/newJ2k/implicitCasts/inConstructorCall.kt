internal class A(d: Double) {
    fun testPrimary(i: Int) {
        println(A(1.0))
        println(A(i.toDouble()))
        val a1 = A(1.0)
        val a2 = A(i.toDouble())
    }
}

internal class B {
    constructor(i: Int) {}
    constructor(s: String?) {}
    constructor(i: Int, s: String?) {}
    constructor(i: Int, d: Double) {}

    fun testSecondary(i: Int) {
        println(B(1, 1.0))
        println(B(i, i.toDouble()))
        val b1 = B(1, 1.0)
        val b2 = B(i, i.toDouble())
    }
}

internal class C(d: Double) {
    fun foo(d: Double) {}
    fun testAnonymousClass(i: Int) {
        Runnable {
            println(C(1.0))
            println(C(i.toDouble()))
            foo(1.0)
            foo(i.toDouble())
        }
    }
}
