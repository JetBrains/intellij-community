// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    fun doSomething(par<caret>am1: String, param: String) {
        println("Value: $value, Param: $param1")
        with(param1) {
            withContext()
        }
    }

    fun inside(param: String) {
        doSomething(param, param)
    }

    fun inside1(param: String) {
        this.doSomething(param, param)
    }

    fun inside2(param: String, another: MyClass) {
        another.doSomething(param, param)
    }
}

context(s: String)
fun withContext() {}

fun MyClass.foo() {
    doSomething("param1", "param2")
}

fun String.bar(m: MyClass) {
    m.doSomething(this, this)
}

class Bar {
    fun String.bar(m: MyClass) {
        m.doSomething(this@bar, this@bar)
    }
}

fun usage() {
    val obj = MyClass()
    obj.doSomething("test", "test1")

    with(obj) {
        doSomething("inside with", "test1")
    }
}