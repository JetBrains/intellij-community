class A {
    fun bar() {

    }

    fun with(a: A, lambda: A.() -> Unit) {}

    fun foo(other: A) {
        <selection>bar()</selection>
        this.bar()
        this@A.bar()
        (bar())
        (this).bar()

        with(other) {
            this.bar()
            bar()
        }
    }
}