// ERROR: This refactoring cannot preserve the initialization of this constructor.
fun record(value: Int) = value

open class Parent(value: Int)
open class <caret>Base(val value: Int) : Parent(record(2))
class Child : Base(record(1))
