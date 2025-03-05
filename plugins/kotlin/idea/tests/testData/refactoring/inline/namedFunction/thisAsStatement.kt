fun foo() {
    bar { val a: Foo = 1.baz() }
}

interface Foo {
    fun Int.b<caret>az(): Foo = this@Foo
    fun m() {
        val f = 2.baz()
    }
}
fun bar(a: Foo.() -> Unit) {}

// IGNORE_K1