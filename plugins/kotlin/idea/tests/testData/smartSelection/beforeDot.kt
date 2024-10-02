class Foo {
    val foo: Foo = this
    val bar: Foo = this

    fun f(f: Foo) {
        val foo1 = f.foo<caret>.bar
    }
}

/*
f.foo
f.foo.bar
*/