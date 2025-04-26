// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(s: String)
fun foo() {}

context(<caret>a: String, s1: String)
fun bar() {
    with("a") {
        foo()
    }
}
// IGNORE_K1