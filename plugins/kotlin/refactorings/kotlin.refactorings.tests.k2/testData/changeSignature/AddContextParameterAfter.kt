// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    fun inside() {
        context(42) {
            doSomething()
        }
    }

    fun inside1() {
        context(42) {
            this@MyClass.doSomething()
        }
    }

    fun inside2(another: MyClass) {
        context(42) {
            another.doSomething()
        }
    }
}

context(i: Int)
fun MyClass.doSomething() {
    println("Value: $value")
}

fun MyClass.foo() {
    context(42) {
        doSomething()
    }
}

fun Int.bar(m: MyClass) {
    context(42) {
        m.doSomething()
    }
}

fun usage() {
    val obj = MyClass()
    context(42) {
        obj.doSomething()
    }

    with(obj) {
        context(42) {
            doSomething()
        }
    }
}