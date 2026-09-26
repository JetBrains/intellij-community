open class Super(val name: String)
class Sub(val s: String) : Super(s)

fun main() {
    val sub: Super = Sub("hello")
    //Breakpoint!
    println()
}

// VARIABLE_PATH: sub.s
// EVAL_EXPRESSION: (sub as Sub).s