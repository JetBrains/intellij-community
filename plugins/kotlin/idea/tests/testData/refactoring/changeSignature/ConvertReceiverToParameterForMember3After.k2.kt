interface A {
    fun f(i1: Int, i: Int)
}

class B : A {
    override fun f(i1: Int, i: Int) {
        println(this@B)
    }
}

fun u(b: B) {
    with(b) {
        f(1, 2)
    }
}
