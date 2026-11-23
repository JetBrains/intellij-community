package genericCastInPackage

open class Super(val name: String)
class Box<T>(val value: T) : Super("box")

fun main() {
    val b: Super = Box("hello")
    //Breakpoint!
    println()
}

// IDEA-335226: in a package the runtime cast names the class by its short name, and it still gets the star projection.
// VARIABLE_PATH: b.value
// EVAL_EXPRESSION: (b as Box<*>).value
