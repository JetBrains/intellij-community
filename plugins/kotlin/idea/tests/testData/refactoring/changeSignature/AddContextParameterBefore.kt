// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    fun inside() {
        doSomething()
    }

    fun inside1() {
        this.doSomething()
    }

    fun inside2(another: MyClass) {
        another.doSomething()
    }
}

fun MyClass.doSometh<caret>ing() {
    println("Value: $value")
}

fun MyClass.foo() {
    doSomething()
}

fun Int.bar(m: MyClass) {
    m.doSomething()
}

fun usage() {
    val obj = MyClass()
    obj.doSomething()
    
    with(obj) {
        doSomething()
    }
}