// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
context(param: String)
fun outerCall() {
    doSomething(param)
}

fun doSomething(param1: String) {
}