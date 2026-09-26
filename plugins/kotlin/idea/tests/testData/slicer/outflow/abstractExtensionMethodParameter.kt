// FLOW: OUT

interface I {
    fun Int.foo(p: Any)
}

class C1 : I {
    override fun Int.foo(p: Any) {
        val v = (p)
    }
}

fun I.bar(s: String) {
    1.foo(<caret>s)
}
