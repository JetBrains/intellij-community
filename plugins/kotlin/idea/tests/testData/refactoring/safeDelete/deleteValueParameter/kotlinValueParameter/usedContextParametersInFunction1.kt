// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

context(<caret>a: String)
val p: Int
    get() {
        foo()
        return 42
    }

context(<caret>a: String)
fun bar() {
    println(p)
}
// IGNORE_K1