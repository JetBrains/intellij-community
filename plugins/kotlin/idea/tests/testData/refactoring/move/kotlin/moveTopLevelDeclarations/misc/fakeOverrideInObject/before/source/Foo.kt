package source

open class MyClass {
    val foo: Int = 1
}

object MyObj: MyClass()

fun <caret>test() {
    MyObj.foo
}