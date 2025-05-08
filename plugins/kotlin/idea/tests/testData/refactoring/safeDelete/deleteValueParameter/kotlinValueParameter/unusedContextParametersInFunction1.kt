// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(<caret>unused: String)
fun foo(b: String) {}

context(a: String)
fun bar(b: String) {
    foo(b)
}

// IGNORE_K1