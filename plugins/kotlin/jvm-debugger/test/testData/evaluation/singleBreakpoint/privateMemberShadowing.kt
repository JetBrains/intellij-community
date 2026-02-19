package privateMember

fun main(args: Array<String>) {
    val base = Base()
    val derived = Derived()
    val derivedAsBase: Base = Derived()

    //Breakpoint!
    args.size
}

class MyClass {
    private fun privateFun() = 1
    private val privateVal = 1

    private class PrivateClass {
        val a = 1
    }
}

open class Base {
    private fun privateFun() = 2
}

class Derived: Base() {
    private fun privateFun() = 3
}

// EXPRESSION: derived.privateFun()
// RESULT: 3: I

// IGNORE_K2