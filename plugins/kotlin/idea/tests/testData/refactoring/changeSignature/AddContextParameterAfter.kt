// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    fun inside() {
        with(42) {
            doSomething()
        }
    }

    fun inside1() {
        with(42) {
            this@MyClass.doSomething()
        }
    }

    fun inside2(another: MyClass) {
        with(42) {
            another.doSomething()
        }
    }
}

context(i: Int) fun MyClass.doSomething() {
    println("Value: $value")
}

fun MyClass.foo() {
    with(42) {
        doSomething()
    }
}

fun Int.bar(m: MyClass) {
    with(42) {
        m.doSomething()
    }
}

fun usage() {
    val obj = MyClass()
    with(42) {
        obj.doSomething()
    }

    with(obj) {
        with(42) {
            doSomething()
        }
    }
}