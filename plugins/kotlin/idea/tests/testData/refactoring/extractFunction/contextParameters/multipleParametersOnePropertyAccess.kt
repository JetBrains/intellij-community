// COMPILER_ARGUMENTS: -Xcontext-parameters
context(x: String, y: Int)
fun foo() {
    <selection>val p1 = p</selection>
}

context(x: String, y: Int)
val p: String
    get() = ""

// IGNORE_K1