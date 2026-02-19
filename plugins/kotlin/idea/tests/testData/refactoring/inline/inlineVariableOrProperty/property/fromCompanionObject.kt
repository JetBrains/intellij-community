class Foo {
    companion object {
        val bar = 10
    }

    fun test() {
        val <caret>foo = bar
        println(foo)
    }
}