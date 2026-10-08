// ERROR: This refactoring does not support constructors with parameters or secondary constructors.
open class <caret>Base(val x: Int)
class Child : Base(1)
