package genericCastInChain

interface I

class Box<T>(val value: T) : I {
    val tag = "box"
}

class Holder(val inner: Any) : I

fun main() {
    val i: I = Holder(Box(5))
    //Breakpoint!
    println()
}

// VARIABLE_PATH: i.inner.tag
// EVAL_EXPRESSION: ((i as Holder).inner as Box<*>).tag
