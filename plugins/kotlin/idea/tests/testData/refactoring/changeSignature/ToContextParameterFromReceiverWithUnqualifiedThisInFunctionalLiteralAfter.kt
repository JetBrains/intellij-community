// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42
    context(i: Int)
    fun doSomething(param1: String, param: String, l: () -> Unit = {}) {}
    fun inside(param: String) {
        with(0) {
            doSomething(param, param, {
                run {
                    val klass: MyClass = this@MyClass
                    this@MyClass.value
                }
            })
        }
    }
}
