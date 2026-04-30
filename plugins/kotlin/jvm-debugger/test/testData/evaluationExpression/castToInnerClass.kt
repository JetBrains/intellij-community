class Outer {
    inner class Inner(val s: String)
}

fun main() {
    val a = Outer().Inner("inner")
    //Breakpoint!
    println(a)
}

// VARIABLE_PATH: a.s
// EVAL_EXPRESSION: a.s
