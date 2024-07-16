interface I {
    fun oldFun(p1: String, p2: Int = 0, p3: Int = 1) = newFun(p1, p2, p3)

    fun newFun(p1: String, p2: Int = 1, p3: Int = 1)
}

abstract class C : I {
    override fun newFun(p1: String, p2: Int, p3: Int) { }
}

fun foo(c: C) {
    c.old<caret>Fun("", 0)
}