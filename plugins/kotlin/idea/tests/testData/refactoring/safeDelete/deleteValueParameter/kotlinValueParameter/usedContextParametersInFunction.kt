// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(s: String)
fun foo() {}

context(<caret>a: String)
fun bar() {
    foo()
}
// IGNORE_K1