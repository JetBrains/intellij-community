package foo

import bar.Bar

class Foo<caret> : Bar() {
    fun test() {
        foo(a)
    }
}