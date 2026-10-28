open class Super(val name: String)
class Box<T>(val value: T) : Super("box")

fun main() {
    val b: Super = Box("hello")
    //Breakpoint!
    println()
}

// VARIABLE_PATH: b.value
// EVAL_EXPRESSION: (b as Box<*>).value
