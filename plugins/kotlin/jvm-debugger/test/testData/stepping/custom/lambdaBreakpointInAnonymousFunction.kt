package lambdaBreakpointInAnonymousFunction

fun main() {
  //Breakpoint! (lambdaOrdinal = 1)
  lambda("first", fun(it: String) {
    lambda("second", fun(it: String) {
      println()
    })
  })

  // RESUME: 1
  lambda("first", fun(it: String) {
    //Breakpoint! (lambdaOrdinal = 1)
    lambda("second", fun(it: String) {
      println()
    })
  })
}

fun lambda(s: String, l1: (String) -> Unit) {
  l1(s)
}
