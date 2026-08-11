// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    fun inside(param: String) {
        context(param) {
            doSomething()
        }
    }

    fun inside1(param: String) {
        context(param) {
            this@MyClass.doSomething()
        }
    }

    fun inside2(param: String, another: MyClass) {
        context(param) {
            another.doSomething()
        }
    }
}

context(param1: String)
fun MyClass.doSomething() {
    println("Value: $value, Param: $param1")
}

fun MyClass.foo() {
    context("param1") {
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
    context("test") {
        obj.doSomething()
    }

    with(obj) {
        context("inside with") {
            doSomething()
        }
    }
}