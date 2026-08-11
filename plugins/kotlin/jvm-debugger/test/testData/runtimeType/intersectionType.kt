package runtimeType

fun main() {
    val obj: Any = object : I1, I2, AC1() {
        override fun myMethod1() = "hello"
        override fun myMethod2() = "world"
        override fun myAbstractMethod() = "abstractMethod"
    }
    //Breakpoint!
    val a = 1
}

abstract class AC1 {
    abstract fun myAbstractMethod(): String
}

interface I1 {
    fun myMethod1(): String
}

interface I2 {
    fun myMethod2(): String
}

// EXPRESSION: obj
// RUNTIME_TYPE: runtimeType.AC1 & runtimeType.I1 & runtimeType.I2
