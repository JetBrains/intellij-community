package sourcePositionForGetter

interface Interface {
    val iInt get() = 10
}

abstract class AbstractClass {
    val aInt get() = 20
}

class TestClass : AbstractClass(), Interface {
    val myInt get() = 30
}

fun main() {
    val instance = TestClass()
    // JUMP TO SOURCE: instance.myInt
    // JUMP TO SOURCE: instance.aInt
    // JUMP TO SOURCE: instance.iInt
    //Breakpoint!
    println("")
}
