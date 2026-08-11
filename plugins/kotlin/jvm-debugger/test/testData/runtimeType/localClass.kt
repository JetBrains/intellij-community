package runtimeType

interface MyI

fun main() {
    class Local : MyI {
        val foo = 100
        fun bar() = 222
    }

    val iface: MyI = Local()
    //Breakpoint!
    println(iface)
}

// EXPRESSION: iface
// RUNTIME_TYPE: Local
