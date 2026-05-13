// BIND_TO test.Bar.Companion.foo

package test

fun test() {
    test.Foo.Companion.<caret>foo()
}

class Foo {
    companion object {
       fun foo() {}
    }
}

class Bar {
    companion object {
        fun foo() {}
    }
}
