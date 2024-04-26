interface A {
    fun Int.<caret>f(i: Int)
}
class B : A {
    override fun Int.f(i: Int) {
        println(i + this)
    }
}