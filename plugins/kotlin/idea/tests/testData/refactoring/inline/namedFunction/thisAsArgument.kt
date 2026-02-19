fun foo() {
    bar { val a: Foo = 1.baz() }
}

interface Foo {
    fun Int.b<caret>az(): Foo = fooBarBaz(this@Foo)

    fun Int.fooBarBaz(foo: Foo): Foo = foo

    fun m() {
        2.baz()
    }
}

fun bar(a: Foo.() -> Unit) {}

// IGNORE_K1