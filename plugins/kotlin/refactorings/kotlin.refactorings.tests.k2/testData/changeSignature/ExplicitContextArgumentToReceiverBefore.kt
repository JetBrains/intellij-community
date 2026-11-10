// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

context(value: String)
fun fo<caret>o() {}

fun call() {
    foo(value = "test")
}
