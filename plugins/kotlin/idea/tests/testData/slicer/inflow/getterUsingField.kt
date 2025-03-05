// FLOW: IN

class A(var b: Boolean) {
    var foo: Int = -1
        get() = if (b) field else 0

    fun test() {
        val x = <caret>foo
        foo = 1
    }
}
