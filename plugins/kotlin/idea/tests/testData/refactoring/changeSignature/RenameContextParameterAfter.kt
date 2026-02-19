// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
context(a: Int)
fun m(b: Int) {
    val sum = b + a
}