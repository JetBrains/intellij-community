package lambdaRightBrace

fun main() {
    listOf(1, 2, 3).apply {
        val i = 1
        val s = "abc"
        //Breakpoint!
        println(i * size)
    }
}

// STEP_OVER: 1

// EXPRESSION: i + s.length
// RESULT: 4: I