package runtimeType

interface I1 {
    fun myMethod1(): String
}

interface I2 {
    fun myMethod2(): String
}

fun main() {
    abstract class AC1 {
        abstract fun myAbstractMethod(): String
    }

    val obj: Any = object : I1, I2, AC1() {
        override fun myMethod1() = "hello"
        override fun myMethod2() = "world"

        override fun myAbstractMethod() = "abstractMethod"
    }
    //Breakpoint!
    println(obj)
}

// EXPRESSION: obj
// RUNTIME_TYPE: AC1 & runtimeType.I1 & runtimeType.I2