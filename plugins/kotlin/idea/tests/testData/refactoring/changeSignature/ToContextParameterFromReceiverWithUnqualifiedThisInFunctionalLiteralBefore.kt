// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42
    fun Int.doSo<caret>mething(param1: String, param: String, l: () -> Unit = {}) {}
    fun inside(param: String) {
        0.doSomething(param, param) {
            run {
                val klass: MyClass = this
                this.value
            }
        }
    }
}