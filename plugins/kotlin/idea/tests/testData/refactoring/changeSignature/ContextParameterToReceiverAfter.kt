// COMPILER_ARGUMENTS: -Xcontext-parameters

fun Int.foo(string: String) {
    this + 5
    string.substring(0)
}
