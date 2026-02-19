package conditionalReturnInLambda

// RESUME: 20
fun main() {
  for (x in listOf(37, 42)) {
    //Breakpoint!, conditionalReturn = true, lambdaOrdinal = 2
    oneTwoOne(x, { println("1") }, { if (it == 37) return@oneTwoOne; println("2") })
  }
}

fun oneTwoOne(v: Int, one: (Int) -> Unit, two: (Int) -> Unit) {
  one(v)
  two(v)
  one(v)
}
