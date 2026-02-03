// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    fun inside(param: String) {
        with(param) {
            doSomething(this)
        }
    }

    fun doSomething(param1: CharSequence) {
    }
}