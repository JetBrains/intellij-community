package getterOfLateinitVariableIsNotShown

class TestClass {
    lateinit var lateinitVar: Any
    init {
        lateinitVar = 1
    }
}

fun main() {
    val instance = TestClass()
    //Breakpoint!
    println()
}

// PRINT_FRAME
