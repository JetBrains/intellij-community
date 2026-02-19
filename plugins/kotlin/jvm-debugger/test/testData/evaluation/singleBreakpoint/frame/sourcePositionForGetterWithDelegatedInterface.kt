package sourcePositionForGetterWithDelegatedInterface

interface Interface {
    val iInt get() = 10
}

class Delegate : Interface {
}

class TestClass(delegate: Delegate) : Interface by delegate {
}

fun main() {
    val instance = TestClass(Delegate())
    //Breakpoint!
    println("")
}

// PRINT_FRAME
