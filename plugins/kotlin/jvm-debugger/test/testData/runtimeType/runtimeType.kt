package runtimeType

open class Base
class Derived : Base()

fun main() {
    val obj: Base = Derived()
    val date: Any = java.util.Date()
    //Breakpoint!
    println(obj.toString() + date.toString())
}

// EXPRESSION: obj
// RUNTIME_TYPE: runtimeType.Derived

// EXPRESSION: date
// RUNTIME_TYPE: java.util.Date
