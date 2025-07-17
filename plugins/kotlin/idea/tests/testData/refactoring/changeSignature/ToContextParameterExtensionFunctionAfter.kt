// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    fun inside(param: String) {
        with(param) {
            doSomething()
        }
    }

    fun inside1(param: String) {
        with(param) {
            this@MyClass.doSomething()
        }
    }

    fun inside2(param: String, another: MyClass) {
        with(param) {
            another.doSomething()
        }
    }
}

context(param1: String)
fun MyClass.doSomething() {
    println("Value: $value, Param: $param1")
}

fun MyClass.foo() {
    with("param1") {
        doSomething()
    }
}

fun String.bar(m: MyClass) {
    with(this) {
        m.doSomething()
    }
}

class Bar {
    fun String.bar(m: MyClass) {
        with(this@bar) {
            m.doSomething()
        }
    }
}

fun usage() {
    val obj = MyClass()
    with("test") {
        obj.doSomething()
    }

    with(obj) {
        with("inside with") {
            doSomething()
        }
    }
}