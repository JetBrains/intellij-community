class Foo {
    fun Bar.foo() {
        this.bar(this@Foo)
        bar(this@Foo)
    }
}

class Bar {
    fun bar(f: Foo) {
    }
}