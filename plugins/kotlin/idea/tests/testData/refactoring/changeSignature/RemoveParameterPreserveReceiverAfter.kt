interface A {
    fun Int.foo()
}
class B: A {
    override fun Int.foo() {}
}
fun usage(b: B) {
    with(b) {
        1.foo()
    }
}