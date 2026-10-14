interface Root<T>
open class Parent(val value: Int)
open class <caret>Base<T> : Parent(42), Root<T>
class Child : Base<String>()
