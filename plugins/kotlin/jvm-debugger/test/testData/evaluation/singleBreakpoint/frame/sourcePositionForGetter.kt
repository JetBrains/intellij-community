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
    //Breakpoint!
    println("")
}

// PRINT_FRAME
