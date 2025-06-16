// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(s: String)
fun foo() {}

context(a: String, s<caret>1: String)
fun bar() {
    with("a") {
        foo()
    }
}
// IGNORE_K1