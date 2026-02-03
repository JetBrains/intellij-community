package multiLineLambda

// RESUME: 20
fun main() {
    setOf(1)
        //Breakpoint!, lambdaOrdinal = 1
        .map { number -> number * 2
        }
        //Breakpoint!, lambdaOrdinal = 1
        .forEach { println(it) }

    setOf(1)
        .map { number ->
            //Breakpoint!
            number * 2
        }
        //Breakpoint!, lambdaOrdinal = 1
        .forEach { println(it) }

    setOf(1)
        .maxOf {
            //Breakpoint!
            it
        }

    setOf(1)
        .maxOf {
            //Breakpoint!, lambdaOrdinal = -1
            it }.toLong()

    setOf(1)
        //Breakpoint!, lambdaOrdinal = 2
        .map { it }.maxOf { it
        }
}
