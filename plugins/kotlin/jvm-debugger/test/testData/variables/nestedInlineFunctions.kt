package nestedInlineFunctions

// SHOW_KOTLIN_VARIABLES

fun main() {
    val x = 0
    f {
        //Breakpoint!
        g(2)
    }
}

inline fun f(block: () -> Unit) {
    var y = 1
    //Breakpoint!
    block()
}

inline fun g(a: Int) {
    var z = 3
    //Breakpoint!
    println()
}
