// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(_: String)
fun foo() {
    ba<caret>r(2)
}

context(s: String)
fun bar(i: Int) {
    s.length
}

// IGNORE_K1