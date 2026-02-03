// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    fun Int.doSomething(par<caret>am1: String, param: String) {
        println("Value: $value, Param: $param1")
        with(param1) {
            withContext()
        }
    }

    fun inside(param: String) {
        0.doSomething(param, param)
    }

    fun inside1(param: String) {
        1.doSomething(param, param)
    }

    fun inside2(param: String, another: MyClass) {
        with(another) {
            2.doSomething(param, param)
        }
    }
}

context(s: String)
fun MyClass.caller() {
    42.doSomething(s, "param")
}

context(s: String)
fun withContext() {}

fun MyClass.foo() {
    3.doSomething("param1", "param2")
}

fun String.bar(m: MyClass) {
    with(m) {
        4.doSomething(this@bar, this@bar)
    }
}

class Bar {
    fun String.bar(m: MyClass) {
        with(m) {
            5.doSomething(this@bar, this@bar)
        }
    }
}

fun usage() {
    val obj = MyClass()
    with(obj) {
        6.doSomething("test", "test1")
    }

    with(obj) {
        7.doSomething("inside with", "test1")
    }
}