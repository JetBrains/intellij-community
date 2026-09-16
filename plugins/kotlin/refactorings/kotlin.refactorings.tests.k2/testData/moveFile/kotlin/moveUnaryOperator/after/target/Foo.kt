package target

class Foo {
    operator fun unaryPlus() {}
}

fun testUnaryPlusCommoner(foo: Foo) {
    +foo
}