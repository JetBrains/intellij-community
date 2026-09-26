package companionObjectGettersAreNotShown

class TestClass {
    companion object {
        val x1 = 1
        val x2 get() = 2
        @JvmStatic
        val x3 = 3
        @JvmStatic
        val x4 get() = 4
    }
}

fun main() {
    val instance = TestClass()
    //Breakpoint!
    println()
}

// PRINT_FRAME
