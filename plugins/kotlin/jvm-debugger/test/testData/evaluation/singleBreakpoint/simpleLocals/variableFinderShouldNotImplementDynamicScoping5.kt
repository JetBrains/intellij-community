package variableFinderShouldNotImplementDynamicScoping5

fun outer(x: Int): () -> Int {
    fun inner(): Int {
        val y = x
        val x = 57
        //Breakpoint!
        return x
    }
    return ::inner
}


fun main() {
    outer(75)()
}

// EXPRESSION: x
// RESULT: 57: I