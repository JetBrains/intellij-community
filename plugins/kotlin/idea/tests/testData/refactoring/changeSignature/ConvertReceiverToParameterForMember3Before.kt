interface A {
    fun Int.<caret>f(i: Int)
}

class B : A {
    override fun Int.f(i: Int) {
        println(this@B)
    }
}

fun u(b: B) {
    with(b) {
        1.f(2)
    }
}
