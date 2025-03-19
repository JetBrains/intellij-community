interface A {
    fun A.<caret>f(i: Int)
}

class B(val p: Int) : A {
    override fun A.f(i: Int) {
        println(this.toString())
        println(this@B.toString())
    }
}

fun main() {
    with(B(1)) {
        B(2).f(1)
    }
}