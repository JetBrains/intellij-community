package protectedPropertyWithoutFieldInBase

fun main() {
    val derived = Derived()
    // EXPRESSION: derived.megaProperty
    // RESULT: 42: I
    //Breakpoint!
    derived.foo()
}

class Derived : Super() {
    fun foo() {
        // EXPRESSION: megaProperty
        // RESULT: 42: I
        //Breakpoint!
        "".toString() // ← breakpoint
    }
}

abstract class Super {
    protected val megaProperty: Int get() = 42
}
