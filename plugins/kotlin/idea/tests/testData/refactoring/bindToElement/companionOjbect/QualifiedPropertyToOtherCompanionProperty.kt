// BIND_TO test.Bar.Companion.foo

package test

fun test() {
    println(test.Foo.Companion.<caret>foo)
}

class Foo {
    companion object {
       val foo: String = "foo"
    }
}

class Bar {
    companion object {
        val foo: String = "foo"
    }
}
