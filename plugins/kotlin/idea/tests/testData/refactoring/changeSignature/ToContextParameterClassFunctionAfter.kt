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
        with(param) {
            doSomething(param)
        }
    }

    fun inside1(param: String) {
        with(param) {
            this@MyClass.doSomething(param)
        }
    }

    fun inside2(param: String, another: MyClass) {
        with(param) {
            another.doSomething(param)
        }
    }
}

context(s: String)
fun withContext() {}

fun MyClass.foo() {
    with("param1") {
        doSomething("param2")
    }
}

fun String.bar(m: MyClass) {
    with(this) {
        m.doSomething(this@bar)
    }
}

class Bar {
    fun String.bar(m: MyClass) {
        with(this@bar) {
            m.doSomething(this@bar)
        }
    }
}

fun usage() {
    val obj = MyClass()
    with("test") {
        obj.doSomething("test1")
    }

    with(obj) {
        with("inside with") {
            doSomething("test1")
        }
    }
}