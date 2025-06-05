// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    context(para<caret>m1: String)
    fun doSomething(param: Int) {
        println("Value: $value, Context Param: $param1, Param: $param")
        withContext()
    }

    fun inside(param: String) {
        with(param) {
            doSomething(42)
        }
    }

    fun inside1(param: String) {
        with (param) {
            doSomething(42)
        }
    }

    fun inside2(param: String, another: MyClass) {
        with (param) {
            with(another) {
                doSomething(42)
            }
        }
    }
}

context(s: String)
fun withContext() {}

context(s: String)
fun MyClass.withContext1() {
    doSomething(42)
}

fun MyClass.foo() {
    with("param1") {
        doSomething(42)
    }
}

fun String.bar(m: MyClass) {
    with(m) { doSomething(42) }
}

class Bar {
    fun String.bar(m: MyClass) {
        with(m) { doSomething(42) }
    }
}

fun usage() {
    val obj = MyClass()
    with ("test") {
        with(obj) { doSomething(42) }
    }

    with(obj) {
        with("inside with") {
            doSomething(42)
        }
    }
}