// COMPILER_ARGUMENTS: -Xcontext-parameters
context(a: String)
fun bar(p: Int) {}

context(a1: String)
fun bar1(p: Int) {}

context(i: Int)
val prop: Int
    get() = 42

context(a: String, b: Int)
fun m() {
    <selection>val i = prop
    bar(i)
    bar1(i)
    </selection>
}

// IGNORE_K1