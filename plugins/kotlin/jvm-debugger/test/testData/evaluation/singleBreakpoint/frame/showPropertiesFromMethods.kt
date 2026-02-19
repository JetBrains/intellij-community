package showPropertiesFromMethods

interface I {
    fun getValue1() = 1
    fun getValue2() = 2
    fun isI() = true
    fun isIFromDerived(): Boolean
}

abstract class A {
    fun getValue3() = 3
    open fun getValue4() = 3
    fun isA() = true

    abstract fun isAFromDerived(): Boolean
}

class TestClass : A(), I {
    override fun getValue2() = 3

    override fun getValue4() = 4

    fun getValue5() = 5

    fun isTestClass() = true

    override fun isIFromDerived() = true

    override fun isAFromDerived() = true
}

fun main() {
    val instance = TestClass()
    //Breakpoint!
    println()
}

// PRINT_FRAME
