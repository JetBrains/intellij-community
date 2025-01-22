// DISABLE_ERRORS
class A(val n: Int, val k: Int)

fun test() {
    <selection>fun <T: A> foo(t: T): T {
        fun A.a(n: Int): Int = this.n + n + k
        fun A.b(n: Int): Int = this.n - n

        t.n + A(1).a(2) - A(2).b(1)
        return t
    }</selection>

    fun <U: A> foo(u: U): U {
        fun A.x(m: Int): Int = n + m + k
        fun A.y(n: Int): Int = this.n - n

        u.n + A(1).x(2) - A(2).y(1)
        return u
    }

    fun <V: A> foo(v: V): V {
        fun A.a(n: Int): Int = this.n + n + k
        fun A.b(n: Int): Int = this.n + n

        v.n + A(1).a(2) - A(2).b(1)
        return v
    }
}