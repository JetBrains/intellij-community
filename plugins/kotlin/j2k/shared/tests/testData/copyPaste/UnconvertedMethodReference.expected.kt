// ERROR: None of the following functions can be called with the arguments supplied:  public open fun printf(l: Locale!, format: String!, vararg args: Any!): PrintStream! defined in java.io.PrintStream public open fun printf(format: String!, vararg args: Any!): PrintStream! defined in java.io.PrintStream
// ERROR: Function invocation 'printf(...)' expected
fun foo() {
    System.out.printf
}