// COMPILER_ARGUMENTS: -Xcontext-parameters
context(_: String)
fun bar(p: Int) {}

context(_: Int)
val prop: Int
    get() = 42

context(a: String, b: Int)
fun m() {
    val i = prop
    <selection>bar(i)</selection>
}

// IGNORE_K1