package foo

import foo.Foo

class <caret>Bar : Foo() {
    fun bar() {
        foo()
    }
}
