package variableFinderShouldNotImplementDynamicScoping

fun outer(x: Int): () -> Int {
    fun inner(): Int {
        return x
    }
    fun returned(): Int {
        //Breakpoint!
        return 0
    }
    return ::returned
}

fun main() {
    val x = 45
    val f = outer(54)
    f()
}

// EXPRESSION: x
// RESULT: 'x' is not captured