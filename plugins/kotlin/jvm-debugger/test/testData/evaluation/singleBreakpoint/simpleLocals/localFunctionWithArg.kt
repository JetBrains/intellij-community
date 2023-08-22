package localFunction

fun main() {
    fun localFunction(x: Int) = 42 + x

    //Breakpoint!
    val a = 1
}

// EXPRESSION: localFunction(8)
// RESULT: 50: I