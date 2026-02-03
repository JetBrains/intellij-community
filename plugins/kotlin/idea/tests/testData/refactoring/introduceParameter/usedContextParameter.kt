// WITH_STDLIB
// WITH_DEFAULT_VALUE: false
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(s: String)
fun foo() {
    bar()
    <selection>s.length</selection>
}

context(s: String)
fun bar() {}

// IGNORE_K1