// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    fun String.doSomething(param: Int) {
        println("Value: ${value}, Context Param: ${this}, Param: $param")
        withContext()
    }

    fun inside(param: String) {
        with(param) {
            this.doSomething(42)
        }
    }

    fun inside1(param: String) {
        with (param) {
            this.doSomething(42)
        }
    }

    fun inside2(param: String, another: MyClass) {
        with (param) {
            with(another) {
                this.doSomething(42)
            }
        }
    }
}

context(s: String)
fun withContext() {}

context(s: String)
fun MyClass.withContext1() {
    s.doSomething(42)
}

fun MyClass.foo() {
    with("param1") {
        this.doSomething(42)
    }
}

fun String.bar(m: MyClass) {
    with(m) { this@bar.doSomething(42) }
}

class Bar {
    fun String.bar(m: MyClass) {
        with(m) { this@bar.doSomething(42) }
    }
}

fun usage() {
    val obj = MyClass()
    with ("test") {
        with(obj) { this.doSomething(42) }
    }

    with(obj) {
        with("inside with") {
            this.doSomething(42)
        }
    }
}