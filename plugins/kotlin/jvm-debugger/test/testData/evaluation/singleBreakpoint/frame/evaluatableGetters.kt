package evaluatableGetters

interface I {
    val i: Int get() = 1
}

abstract class Base {
    val base: Int get() = 2
}

class X : Base(), I {
    val a = 1

    fun getB(arg: Int): Int = 5

    val b: Int
        get() = 1 + 1

    val c: String = ""
        get() = field + ""

    var d = ""

    var e: Int = 1
        get() = field + 1
        set(value) { field = value + 1 }

    lateinit var f: String

    fun getG(): String = ""

    val `😎`: Int
        get() = 1

    val `""`: Int
        get() = 2
}

fun main() {
    val x = X()
    //Breakpoint!
    println()
}

// PRINT_FRAME
