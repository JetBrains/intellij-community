// WITH_DEFAULT_VALUE: false
interface I {
    fun bar(): Int
}

open class A {
    open fun foo(i: I) {}
}

class B: A() {
    override fun foo(i: I) {
        <selection>i.bar()</selection>
    }
}