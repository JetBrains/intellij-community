package recursive

fun main() {
    x(2)
}

fun x(i: Int) {
    //Breakpoint!
    if (i == 2) {
        y()
    }
    println(i)
}

fun y() {
    x(1)
}

// STEP_OVER: 10
