package objectFields

// SHOW_KOTLIN_VARIABLES
// PRINT_FRAME

object Obj {
    val data = "data"
    val i = 1

    fun foo() {
        //Breakpoint!
        println()
    }
}

fun main() {
    Obj.foo()
}
