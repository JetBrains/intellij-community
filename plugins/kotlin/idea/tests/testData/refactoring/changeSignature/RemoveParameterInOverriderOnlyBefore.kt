interface A {
    fun m(i: Int)
}

class B: A {
    override fun <caret>m(i: Int) {}
}