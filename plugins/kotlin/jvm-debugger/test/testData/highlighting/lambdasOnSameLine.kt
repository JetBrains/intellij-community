package lambdasOnSameLine

fun main() {
  //Breakpoint!, lambdaOrdinal = 1
  lambda("first") { lambda("second") { println("third $it ") } }

  // RESUME: 1
  //Breakpoint!, lambdaOrdinal = 1
  lambda("first") {
    lambda("second", null)
  }

  // RESUME: 1
  //Breakpoint!, lambdaOrdinal = 1
  lambda("first") {
    lambda("second") { println("third $it") }
  }
}

fun <T> lambda(obj: T, f: ((T) -> Unit)?) = if (f == null) Unit else f(obj)
