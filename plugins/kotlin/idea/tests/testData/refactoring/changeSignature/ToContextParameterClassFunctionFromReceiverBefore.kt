// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    fun String.doS<caret>omething() {
        println("Value: ${value}, Param: ${this}")
        withContext()
    }

    fun inside(param: String) {
        param.doSomething()
    }

    fun inside2(param: String, another: MyClass) {
        with(another) {
            param.doSomething()
        }
    }
}

context(s: String)
fun withContext() {}

fun MyClass.foo() {
    "param1".doSomething()
}

fun String.bar(m: MyClass) {
    with(m) { this@bar.doSomething() }
}

class Bar {
    fun String.bar(m: MyClass) {
        with(m) { doSomething() }
    }
}

fun usage() {
    val obj = MyClass()
    with(obj) { "test".doSomething() }

    with(obj) {
        with("inside with") {
            doSomething()
        }
    }
}