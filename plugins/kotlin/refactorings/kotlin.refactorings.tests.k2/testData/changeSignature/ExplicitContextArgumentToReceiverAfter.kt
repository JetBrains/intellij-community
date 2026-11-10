// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2

fun String.foo() {}

fun call() {
    "test".foo()
}
