package bar

class A {
    fun m() {}
    fun foo(a: Any) {
        if (a is A) {
            a.m()
        }
    }
}