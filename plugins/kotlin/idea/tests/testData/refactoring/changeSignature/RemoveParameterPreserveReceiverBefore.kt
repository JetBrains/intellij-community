interface A {
    fun Int.f<caret>oo(i: Int)
}
class B: A {
    override fun Int.foo(i: Int) {}
}
fun usage(b: B) {
    with(b) {
        1.foo(2)
    }
}