object Foo {
    fun <caret>foo(b: Bar) {
        b.bar(this)
        b.bar(this@Foo)
    }
}

class Bar {
    fun bar(f: Foo) {
    }
}