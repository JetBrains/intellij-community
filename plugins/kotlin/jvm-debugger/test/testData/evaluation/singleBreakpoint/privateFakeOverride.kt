open class Open {
    private val privateVal = 42
    private fun privateFun() = 43
}

class Child : Open()

fun main() {
    val child = Child()
    //Breakpoint!
    val x = 1
}

// EXPRESSION: child.privateVal
// RESULT: 42: I

// EXPRESSION: child.privateFun()
// RESULT: 43: I