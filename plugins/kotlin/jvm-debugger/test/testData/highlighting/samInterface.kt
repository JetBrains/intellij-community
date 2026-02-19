package samInterface

fun main() {
  // RESUME: 1
  //Breakpoint!, lambdaOrdinal = 1
  val samGood = SAMInterface { it % 2 == 0 }
  samGood.accept(5)

  // RESUME: 1
  //Breakpoint!, lambdaOrdinal = 1
  val samBad = SAMInterface { println(); it % 2 == 0 }
  samBad.accept(5)
}

fun interface SAMInterface {
  fun accept(i: Int): Boolean
}