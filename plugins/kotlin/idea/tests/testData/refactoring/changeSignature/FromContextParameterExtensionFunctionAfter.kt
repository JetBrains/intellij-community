// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

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

fun MyClass.doSomething(param1: String) {
    println("Value: $value, Param: $param1")
}

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
