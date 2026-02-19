package annotationsAreNotShown

class TestClass {
    @Suppress("")
    val value get() = 20
}

fun main(args: Array<String>) {
    val instance = TestClass()
    //Breakpoint!
    println("")
}

// PRINT_FRAME
