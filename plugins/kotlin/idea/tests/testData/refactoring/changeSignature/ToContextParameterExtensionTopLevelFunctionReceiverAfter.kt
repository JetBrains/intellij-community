// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

class MyClass {
    val value: Int = 42

    fun inside(param: String) {
        doSomething(param)
    }

    fun inside1(param: String) {
        with(this) {
            doSomething(param)
        }
    }

    fun inside2(param: String, another: MyClass) {
        with(another) {
            doSomething(param)
        }
    }
}

context(klass: MyClass) fun doSomething(param1: String) {
    println("Value: ${klass.value}, Param: $param1")
}

fun MyClass.foo() {
    doSomething("param1")
}

fun String.bar(m: MyClass) {
    with(m) {
        doSomething(this@bar)
    }
}

class Bar {
    fun String.bar(m: MyClass) {
        with(m) {
            doSomething(this@bar)
        }
    }
}

fun usage() {
    val obj = MyClass()
    with(obj) {
        doSomething("test")
    }

    with(obj) {
        doSomething("inside with")
    }
}