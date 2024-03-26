class Holder(val foo: Foo)
class Foo {
    val wrapper = SomeWrapper()
}

class SomeWrapper {
    operator fun invoke() = ""
}

fun someFun(list: List<Holder>) {
    list.map { <selection>it.foo.wrapper().length</selection> }
}

// IGNORE_K1