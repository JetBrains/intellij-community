package breakpointInOneLineLambda

fun main() {
    // RESUME: 6
    setOf(1,2,3).stream()
        //Breakpoint!, lambdaOrdinal = 1
        .map { number ->  number * 2 }
        //Breakpoint!, lambdaOrdinal = 1
        .forEach { println(it) }

    // RESUME: 2
    setOf(1, 2, 3).stream()
        //Breakpoint!, lambdaOrdinal = 1
        .max { o1, o2 -> o1 - o2 }
        .get()

    // RESUME: 3
    //Breakpoint!, lambdaOrdinal = 1
    runThreeTimes { println() }

    setOf(1,2,3).stream()
      // STEP_INTO: 1
      // RESUME: 3
      //Breakpoint!, lambdaOrdinal = 2
      .map { number ->  number * 2 }.map { foo(it) }
      .forEach { println(it) }
}

fun runThreeTimes(action: () -> Unit) {
    repeat(3) {
        action()
    }
}

fun foo(x: Int) = x
