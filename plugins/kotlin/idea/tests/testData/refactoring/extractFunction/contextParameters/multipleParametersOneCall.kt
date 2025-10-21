// COMPILER_ARGUMENTS: -Xcontext-parameters
context(x: String, y: Int)
fun foo() {
    <selection>bar(true)</selection>
}

context(x: String, y: Int)
fun bar(z: Boolean) {}
// IGNORE_K1