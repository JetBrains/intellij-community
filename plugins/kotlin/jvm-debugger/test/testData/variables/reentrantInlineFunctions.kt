package reentrantInlineFunctions

// SHOW_KOTLIN_VARIABLES

fun main() {
    val x = 0
    //Breakpoint!
    f(true) { x ->
        //Breakpoint!
        f(false) { x ->
            //Breakpoint!
            println()
        }
    }
}

inline fun f(b: Boolean, block: (Int) -> Unit) {
    if (b) {
        val x = 1
        //Breakpoint!
        block(x)
    } else {
        //Breakpoint!
        block(2)
    }
}
