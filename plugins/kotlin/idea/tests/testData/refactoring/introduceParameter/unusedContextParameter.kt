// WITH_STDLIB
// WITH_DEFAULT_VALUE: false
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(s: String)
fun foo() {
    <selection>s.length</selection>
}

// IGNORE_K1