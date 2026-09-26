interface A {
    fun f(i1: Int, i: Int)
}
class B : A {
    override fun f(i1: Int, i: Int) {
        println(i + i1)
    }
}