package anonymousFun


fun main(args: Array<String>) {
  // RESUME: 1
  //Breakpoint!, lambdaOrdinal = 1
  foo { boo() }

  // RESUME: 1
  //Breakpoint!, lambdaOrdinal = 1
  foo(fun() { boo() })

  // SMART_STEP_INTO_BY_INDEX: 2
  // RESUME: 1
  //Breakpoint!, lambdaOrdinal = -1
  foo(fun() = boo())
}

fun foo(l: () -> Unit) = l()
fun boo() = Unit
