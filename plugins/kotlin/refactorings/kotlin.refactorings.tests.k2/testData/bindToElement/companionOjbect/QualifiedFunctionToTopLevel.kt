// BIND_TO test.foo

package test

fun test() {
    test.Foo.Companion.<caret>foo()
}

class Foo {
    companion object {
       fun foo() {}
    }
}

fun foo() {
}
