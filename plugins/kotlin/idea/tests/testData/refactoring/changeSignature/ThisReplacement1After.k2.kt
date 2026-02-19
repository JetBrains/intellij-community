interface A {
    fun f(a: A, i: Int)
}

class B(val p: Int) : A {
    override fun f(a: A, i: Int) {
        println(a.toString())
        println(this@B.toString())
    }
}

fun main() {
    with(B(1)) {
        f(B(2), 1)
    }
}
