fun main() {
    val a = true
    inlineFun(a)
}

inline fun inlineFun(condition: Boolean) {
    //Breakpoint!
    if (condition) println("hello")
}

// EXPRESSION: condition
// RESULT: 1: Z
