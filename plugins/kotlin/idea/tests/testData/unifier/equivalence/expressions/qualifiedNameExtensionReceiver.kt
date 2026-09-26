// ERROR: Unresolved reference: B
package p.q.r

val n = 1

class A {

    class B

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

val A.B.n get() = 1

fun foo() {
    B().n
    A.B().n
    p.q.r.A.B().n
}