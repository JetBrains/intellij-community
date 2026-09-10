class X {
    fun oldFun(c1: Char, c2: Char): Char = c1.newFun(this, c2)

    val c: Char = 'a'
}

fun Char.newFun(x: X, c: Char): Char = this

fun foo(s: String, x: X) {
    val chars = s.filter {
        O.x?.old<caret>Fun(it, x.c) != 'a'
    }
}

object O {
    var x: X? = null
}