// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
context(c<caret>1: String, c2: Int)
fun foo(p1: Double, p2: Float) {
}

context(c1: String, c2: Int)
fun bar() {
    foo(1.0, 2.0f)
}