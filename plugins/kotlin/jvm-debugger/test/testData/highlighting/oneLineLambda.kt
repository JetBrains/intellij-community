package oneLineLambda

// RESUME: 20
fun main() {
  setOf(1)
    //Breakpoint!, lambdaOrdinal = 1
    .map { number ->  number * 2 }
    //Breakpoint!, lambdaOrdinal = 1
    .forEach { println(it) }

  setOf(1)
    //Breakpoint!, lambdaOrdinal = 1
    .maxOf { it }
}
