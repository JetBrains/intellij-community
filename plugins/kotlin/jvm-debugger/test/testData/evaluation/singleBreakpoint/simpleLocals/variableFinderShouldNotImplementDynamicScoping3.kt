package variableFinderShouldNotImplementDynamicScoping3

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

fun test(x: Int) {
    fun inner(x: Int) {
        val y = x
        val f = outer(54)
        f()
    }
    inner(x)
}

fun main() {
   test(45)
}

// EXPRESSION: x
// RESULT: Cannot find local variable 'x' with type int