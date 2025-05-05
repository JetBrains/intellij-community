// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    fun inside(param: String) {
        with(param) {
            doSomething()
        }
    }

    context(para<caret>m1: CharSequence)
    fun doSomething() {
    }
}