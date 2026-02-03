fun main() {
    //Breakpoint!
    val sam = Checker { it % 2 == 0 }
}

fun check(value: Int, checker: Checker): Boolean {
    return checker.accept(value)
}

fun interface Checker {
    fun accept(value: Int): Boolean
}

// EXPRESSION: check(1, Checker { it % 2 == 0 })
// RESULT: 0: Z