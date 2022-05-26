package getterOfLateinitVariableIsNotShown

class TestClass {
    lateinit var lateinitVar: Any
    lateinit var lateinitStringVar: String

    init {
        lateinitVar = 1
        lateinitStringVar = ""
    }
}

fun main() {
    val instance = TestClass()
    //Breakpoint!
    println()
}

// PRINT_FRAME
