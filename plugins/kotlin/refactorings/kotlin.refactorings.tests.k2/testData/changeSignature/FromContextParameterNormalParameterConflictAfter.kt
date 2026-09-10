// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
context(c2: Int)
fun foo(c1: String, p1: Double, p2: Float) {
}

context(c1: String, c2: Int)
fun bar() {
    foo(c1, 1.0, 2.0f)
}