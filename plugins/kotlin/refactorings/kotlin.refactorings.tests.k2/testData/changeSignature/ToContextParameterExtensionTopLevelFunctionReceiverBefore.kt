// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

class MyClass {
    val value: Int = 42

    fun inside(param: String) {
        doSomething(param)
    }

    fun inside1(param: String) {
        this.doSomething(param)
    }

    fun inside2(param: String, another: MyClass) {
        another.doSomething(param)
    }
}

private fun MyClass.doS<caret>omething(param1: String) {
    println("Value: $value, Param: $param1")
}

fun MyClass.foo() {
    doSomething("param1")
}

fun String.bar(m: MyClass) {
    m.doSomething(this)
}

class Bar {
    fun String.bar(m: MyClass) {
        m.doSomething(this@bar)
    }
}

fun usage() {
    val obj = MyClass()
    obj.doSomething("test")
    
    with(obj) {
        doSomething("inside with")
    }
}