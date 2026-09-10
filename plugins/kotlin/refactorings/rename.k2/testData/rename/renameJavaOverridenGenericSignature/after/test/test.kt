package test

class FooImpl : Foo {
    override fun <T : Any?> renamedBaz(foo: T) = Unit
}