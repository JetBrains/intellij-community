open class Super(val name: String)
class Sub(s: String) : Super(s)

fun main() {
    val sub = Sub("hello")
    //Breakpoint!
    println()
}

// VARIABLE_PATH: sub.name
// EVAL_EXPRESSION: sub.name