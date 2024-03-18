class A {
    fun bar() {

    }

    fun with(a: A, lambda: A.() -> Unit) {}

    fun foo(other: A) {
        bar()
        <selection>this.bar()</selection>
        this@A.bar()
        (bar())
        (this).bar()

        with(other) {
            this.bar()
            bar()
        }
    }
}