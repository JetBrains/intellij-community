// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(a: String)
val l: Int
    get() = a.length

context(b: String)
fun f() {
    println(<caret>l)
}


// IGNORE_K1