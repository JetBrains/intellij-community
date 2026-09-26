package sequenceLambda

class Context

fun main() {

    val sc = Context()
    val foo = "123"

    sc.apply {
        sequence<String> {
            println(foo)
            println(this@apply)
            //Breakpoint!
            println(this)
        }.take(10).toList()
    }
}

// SHOW_KOTLIN_VARIABLES
// PRINT_FRAME
