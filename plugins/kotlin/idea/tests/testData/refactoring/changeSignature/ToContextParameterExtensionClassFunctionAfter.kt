// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    context(param1: String)
    fun Int.doSomething(param: String) {
        println("Value: $value, Param: $param1")
        with(param1) {
            withContext()
        }
    }

    fun inside(param: String) {
        with(param) {
            0.doSomething(param)
        }
    }

    fun inside1(param: String) {
        with(param) {
            1.doSomething(param)
        }
    }

    fun inside2(param: String, another: MyClass) {
        with(another) {
            with(param) {
                2.doSomething(param)
            }
        }
    }
}

context(s: String)
fun MyClass.caller() {
    42.doSomething("param")
}

context(s: String)
fun withContext() {}

fun MyClass.foo() {
    with("param1") {
        3.doSomething("param2")
    }
}

fun String.bar(m: MyClass) {
    with(m) {
        4.doSomething(this@bar)
    }
}

class Bar {
    fun String.bar(m: MyClass) {
        with(m) {
            5.doSomething(this@bar)
        }
    }
}

fun usage() {
    val obj = MyClass()
    with(obj) {
        with("test") {
            6.doSomething("test1")
        }
    }

    with(obj) {
        with("inside with") {
            7.doSomething("test1")
        }
    }
}
