package p.q.r

fun foo(): Int = 1

class Bar {
    fun foo(): Int = 1
}

fun with(bar: Bar, lambda: Bar.() -> Unit) {}

fun test(bar: Bar?) {
    <selection>p.q.r.foo()</selection>
    bar?.foo()

    if (bar != null) {
        bar.foo()
        with(bar) {
            foo()
        }
    }

    foo()

    fun baz() {
        p.q.r.foo()
    }
}
