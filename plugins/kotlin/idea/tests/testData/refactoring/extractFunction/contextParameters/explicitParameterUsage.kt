// COMPILER_ARGUMENTS: -Xcontext-parameters
context(a: String)
fun bar(p: Int) {}

context(a: String, b: Int)
fun m() {
    <selection>bar(b)</selection>
}

// IGNORE_K1