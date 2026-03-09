// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(s<caret>tr: String)
fun foo(block: context(String) () -> Unit) {
    block()
}
// IGNORE_K1