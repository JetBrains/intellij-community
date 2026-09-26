// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
context(i: Int, s: String)
fun doSomething() {
}

fun usage() {
    context(42, "ctx") {
        doSomething()
    }
}
