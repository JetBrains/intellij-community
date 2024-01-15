// ERROR: None of the following functions can be called with the arguments supplied:  public open fun printf(p0: Locale!, p1: String!, vararg p2: Any!): PrintStream! defined in java.io.PrintStream public open fun printf(p0: String!, vararg p1: Any!): PrintStream! defined in java.io.PrintStream
// ERROR: Function invocation 'printf(...)' expected
fun foo() {
    System.out.printf
}