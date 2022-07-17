class C(private val /*rename*/foo: Int) {
    fun f() = foo
}

fun test() {
    C(foo = 1)
}