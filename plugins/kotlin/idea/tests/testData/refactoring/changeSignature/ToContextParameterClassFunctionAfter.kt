// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    context(param1: String)
    fun doSomething(param: String) {
        println("Value: $value, Param: $param1")
        with(param1) {
            withContext()
        }
    }

    fun inside(param: String) {
        context(param) {
            doSomething(param)
        }
    }

    fun inside1(param: String) {
        context(param) {
            this@MyClass.doSomething(param)
        }
    }

    fun inside2(param: String, another: MyClass) {
        context(param) {
            another.doSomething(param)
        }
    }
}

context(s: String)
fun withContext() {}

fun MyClass.foo() {
    context("param1") {
        doSomething("param2")
    }
}

fun String.bar(m: MyClass) {
    m.doSomething(this)
}

class Bar {
    fun String.bar(m: MyClass) {
        m.doSomething(this@bar)
    }
}

fun usage() {
    val obj = MyClass()
    context("test") {
        obj.doSomething("test1")
    }

    with(obj) {
        context("inside with") {
            doSomething("test1")
        }
    }
}