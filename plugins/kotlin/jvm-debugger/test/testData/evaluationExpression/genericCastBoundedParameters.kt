package genericCastBoundedParameters

interface I

class Bounded<out T : Number, in R : CharSequence>(val n: T) : I

fun main() {
    val i: I = Bounded<Int, String>(9)
    //Breakpoint!
    println()
}

// VARIABLE_PATH: i.n
// EVAL_EXPRESSION: (i as Bounded<*, *>).n
