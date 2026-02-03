// ERROR: Unresolved reference: B
package p.q.r

val n = 1

class A {
    class B {
        val n = 1
    }

    fun with(b: B, lambda: B.() -> Unit) {}

    fun foo() {
        <selection>B().n</selection>
        A.B().n
        p.q.r.A.B().n
        p.q.r.n
        B()?.n
        with(B()) {
            n
        }
        n
    }
}

fun foo() {
    B().n
    A.B().n
    p.q.r.A.B().n
    (A.B()).n
    (A.B().n)
}