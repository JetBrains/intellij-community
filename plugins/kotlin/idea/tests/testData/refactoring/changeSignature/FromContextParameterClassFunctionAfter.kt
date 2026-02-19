// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    context(s: String)
    val value: Int
        get() = 42

    fun doSomething(param1: String) {
        println("Value: ${
            with(param1) {
                value
            }
        }, Param: $param1")
        with(param1) {
            withContext()
        }
    }

    fun inside(param: String) {
        with(param) {
            doSomething(this)
        }
    }

    fun inside1(param: String) {
        with (param) {
            doSomething(this)
        }
    }

    fun inside2(param: String, another: MyClass) {
        with (param) {
            another.doSomething(this)
        }
    }
}

context(s: String)
fun withContext() {}

fun MyClass.foo() {
    with("param1") {
        doSomething(this)
    }
}

fun String.bar(m: MyClass) {
    m.doSomething(this@bar)
}

class Bar {
    fun String.bar(m: MyClass) {
        m.doSomething(this@bar)
    }
}

fun usage() {
    val obj = MyClass()
    with ("test") {
        obj.doSomething(this)
    }

    with(obj) {
        with("inside with") {
            doSomething(this)
        }
    }
}
