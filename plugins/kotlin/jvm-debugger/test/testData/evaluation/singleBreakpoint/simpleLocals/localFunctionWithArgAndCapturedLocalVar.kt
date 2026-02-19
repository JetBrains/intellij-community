package localFunction

fun main() {
    val x: Int = 42
    fun localFunction(y: Int) = x + y

    //Breakpoint!
    val a = 1
}

// EXPRESSION: localFunction(8)
// RESULT: 50: I