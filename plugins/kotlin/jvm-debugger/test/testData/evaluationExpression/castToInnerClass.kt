class Outer {
    inner class Inner(val s: String)
}

class GenericOuter<T> {
    inner class Inner(val s: String)
}

fun main() {
    val a = Outer().Inner("inner")
    val b: Any = GenericOuter<Int>().Inner("generic")
    //Breakpoint!
    println(a)
}

// VARIABLE_PATH: a.s
// EVAL_EXPRESSION: a.s
// VARIABLE_PATH: b.s
// EVAL_EXPRESSION: (b as GenericOuter<*>.Inner).s
