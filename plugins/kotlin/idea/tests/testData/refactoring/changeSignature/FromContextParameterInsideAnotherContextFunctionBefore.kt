// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
context(param: String)
fun outerCall() {
    doSomething()
}

context(para<caret>m1: String)
fun doSomething() {
}