// FLOW: OUT

interface I {
    fun foo(p: Any)
}

class C : I {
    override fun foo(p: Any) {
        val v = p
    }
}

fun bar(i: I, s: String) {
    i.foo(<caret>s)
}
