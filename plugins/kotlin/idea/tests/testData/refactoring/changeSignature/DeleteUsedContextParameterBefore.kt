// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    context(para<caret>m1: String)
    fun doSomething() {
        println("Value: $value, Param: $param1")
        withContext()
    }
}