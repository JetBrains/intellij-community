fun foo() {
    bar {
        1.baz()
    }
}

interface Foo {
    fun Int.b<caret>az() = this@Foo.foobs(this)
    fun foobs(a: Int)
    fun m() {
        1.baz()
    }
}

fun bar(a: Foo.() -> Unit) {}

// IGNORE_K1