package lambdasOnSameLine

fun main() {
  //Breakpoint!, lambdaOrdinal = 1
  lambda("first") { lambda("second") { println("third $it ") } }

  lambda("first") {
    // RESUME: 1
    //Breakpoint!
    lambda("second", null)
  }

  lambda("first") {
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = -1
    lambda("second") { println("third $it") }
  }
}

fun <T> lambda(obj: T, f: ((T) -> Unit)?) = if (f == null) Unit else f(obj)
