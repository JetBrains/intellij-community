interface I {
    fun foo(): Int
}

class A

fun box() : Int {
    val o = object : I, A() {
        override fun foo(): Int = 42
    }
    return o.foo()
}
