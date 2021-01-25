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
    // JUMP TO SOURCE: instance.iInt
    //Breakpoint!
    println("")
}