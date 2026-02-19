package kt23064

fun main(args: Array<String>) {
  val data = SomeData()
  val numbers = arrayListOf(1, 2, 3, 4, 5)

  //Breakpoint!, lambdaOrdinal = 1
  data.let { it: SomeData -> someExtraFun(it) }

  //Breakpoint!, lambdaOrdinal = 1
  data.apply { println(this) }

  //Breakpoint!, lambdaOrdinal = 2
  numbers.run { filter { (it == 5) } }

  //Breakpoint!, lambdaOrdinal = 1
  with(data) { println(this) }

  //Breakpoint!, lambdaOrdinal = 1
  data.also { println(it) }
}

class SomeData

fun someExtraFun(d: SomeData) {
  println(d)
}

// RESUME: 20
