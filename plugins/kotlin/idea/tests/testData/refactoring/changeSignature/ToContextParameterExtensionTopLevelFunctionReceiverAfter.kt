// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

class MyClass {
    val value: Int = 42

    fun inside(param: String) {
        doSomething(param)
    }

    fun inside1(param: String) {
        doSomething(param)
    }

    fun inside2(param: String, another: MyClass) {
        context(another) {
            doSomething(param)
        }
    }
}

context(klass: MyClass)
private fun doSomething(param1: String) {
    println("Value: ${klass.value}, Param: $param1")
}

fun MyClass.foo() {
    doSomething("param1")
}

fun String.bar(m: MyClass) {
    context(m) {
        doSomething(this@bar)
    }
}

class Bar {
    fun String.bar(m: MyClass) {
        context(m) {
            doSomething(this@bar)
        }
    }
}

fun usage() {
    val obj = MyClass()
    context(obj) {
        doSomething("test")
    }

    with(obj) {
        doSomething("inside with")
    }
}
