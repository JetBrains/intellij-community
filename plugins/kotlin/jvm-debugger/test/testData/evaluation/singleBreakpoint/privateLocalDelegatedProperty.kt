class Main {
    fun foo(): Int {
        //Breakpoint!
        return 42
    }
    private val privateDelegatedProperty: Int by lazy { 24 }
}

fun main() {
    Main().foo()
}

// EXPRESSION: privateDelegatedProperty
// RESULT: 24: I