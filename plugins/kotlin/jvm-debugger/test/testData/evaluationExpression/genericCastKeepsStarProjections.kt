open class Super(val name: String)
class Pair2<A, B>(val first: A, val second: B) : Super("pair")

fun main() {
    val p: Super = Pair2("a", 1)
    //Breakpoint!
    println()
}

// VARIABLE_PATH: p.first
// EVAL_EXPRESSION: (p as Pair2<*, *>).first
