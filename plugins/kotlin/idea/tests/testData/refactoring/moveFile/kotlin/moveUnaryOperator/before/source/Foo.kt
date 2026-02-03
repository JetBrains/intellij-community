package source

class Foo {
    operator fun unaryPlus() {}
}

fun testUnaryPlusCommoner(foo: Foo) {
    +foo
}