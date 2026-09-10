// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    context(s: String)
    val value: Int
        get() = 42

    context(par<caret>am1: String)
    fun doSomething() {
        println("Value: $value, Param: $param1")
        withContext()
    }

    fun inside(param: String) {
        with(param) {
            doSomething()
        }
    }

    fun inside1(param: String) {
        with (param) {
            doSomething()
        }
    }

    fun inside2(param: String, another: MyClass) {
        with (param) {
            another.doSomething()
        }
    }
}

context(s: String)
fun withContext() {}

fun MyClass.foo() {
    with("param1") {
        doSomething()
    }
}

fun String.bar(m: MyClass) {
    m.doSomething()
}

class Bar {
    fun String.bar(m: MyClass) {
        m.doSomething()
    }
}

fun usage() {
    val obj = MyClass()
    with ("test") {
        obj.doSomething()
    }

    with(obj) {
        with("inside with") {
            doSomething()
        }
    }
}