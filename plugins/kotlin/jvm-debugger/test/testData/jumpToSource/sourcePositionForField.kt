package sourcePositionForField

abstract class AbstractClass {
    val aInt = 10
}

class TestClass : AbstractClass() {
    val myInt = 20
}

fun main() {
    val instance = TestClass()
    // JUMP TO SOURCE: instance.myInt
    // JUMP TO SOURCE: instance.aInt
    //Breakpoint!
    println("")
}
