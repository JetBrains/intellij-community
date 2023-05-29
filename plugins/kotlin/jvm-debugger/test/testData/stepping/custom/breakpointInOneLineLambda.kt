package breakpointInOneLineLambda

// RESUME: 20
fun main() {
    setOf(1,2,3).stream()
        //Breakpoint!, lambdaOrdinal = 1
        .map { number ->  number * 2 }
        //Breakpoint!, lambdaOrdinal = 1
        .forEach { println(it) }

    setOf(1, 2, 3).stream()
        //Breakpoint!, lambdaOrdinal = 1
        .max { o1, o2 -> o1 - o2 }
        .get()

    //Breakpoint!, lambdaOrdinal = 1
    runThreeTimes { println() }
}

fun runThreeTimes(action: () -> Unit) {
    repeat(3) {
        action()
    }
}
